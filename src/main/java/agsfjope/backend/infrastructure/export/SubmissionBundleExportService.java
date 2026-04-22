package agsfjope.backend.infrastructure.export;

import agsfjope.backend.application.dtos.responses.grading.AnswerGradingDetail;
import agsfjope.backend.application.dtos.responses.grading.GradingResultResponse;
import agsfjope.backend.application.gradingservices.GradingQueryService;
import agsfjope.backend.configuration.storage.MinioConfig;
import agsfjope.backend.core.entities.Block;
import agsfjope.backend.core.entities.Submission;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.repositories.block.BlockRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import agsfjope.backend.application.ports.out.FileStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the block-level export zip used by exam staff.
 *
 * <p>Zip layout:</p>
 * <pre>
 * {examName}/
 *   grading/
 *     {submissionFileBase}/oopvalidation.txt
 *   submission/
 *     {submissionFileName}
 * </pre>
 *
 * <p>The grading folder name always matches the exported submission file name
 * without its extension, so staff can pair the review text with the original
 * student archive directly.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionBundleExportService {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final BlockRepository blockRepository;
    private final SubmissionRepository submissionRepository;
    private final GradingQueryService gradingQueryService;
    private final FileStoragePort fileStorageService;
    private final MinioConfig minioConfig;

    /**
     * Generates the export zip for one block.
     *
     * @param examId exam UUID used to validate ownership
     * @param blockId block UUID
     * @return zip bytes
     * @throws IOException if zip serialization fails
     */
    public byte[] generateSubmissionBundle(UUID examId, UUID blockId) throws IOException {
        Block block = blockRepository.findByBlockIdWithExam(blockId)
                .orElseThrow(() -> new NotFoundException("Block không tồn tại."));

        if (block.getExam() == null || !block.getExam().getExamId().equals(examId)) {
            throw new IllegalArgumentException("Block không thuộc kỳ thi được yêu cầu.");
        }

        List<Submission> submissions = submissionRepository.findAllByBlock_BlockIdOrderBySubmittedAtDesc(blockId);
        Map<UUID, GradingResultResponse> detailBySubmissionId = gradingQueryService
                .getBlockResultsWithDetails(blockId)
                .stream()
                .filter(item -> item.getSubmissionId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        GradingResultResponse::getSubmissionId,
                        item -> item,
                        (a, b) -> a,
                        HashMap::new
                ));

        String rootFolder = sanitizePathSegment(
                block.getExam() != null ? block.getExam().getName() : null,
                "exam"
        );

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {

            addDirectoryEntry(zos, rootFolder + "/");
            addDirectoryEntry(zos, rootFolder + "/grading/");
            addDirectoryEntry(zos, rootFolder + "/submission/");

            Set<String> usedSubmissionNames = new HashSet<>();

            for (Submission submission : submissions) {
                String originalFileName = safeArchiveFileName(submission.getFileName());
                String exportedFileName = allocateUniqueFileName(originalFileName, usedSubmissionNames);
                String gradingFolderName = stripExtension(exportedFileName);

                addDirectoryEntry(zos, rootFolder + "/grading/" + gradingFolderName + "/");
                addTextEntry(
                        zos,
                        rootFolder + "/grading/" + gradingFolderName + "/oopvalidation.txt",
                        buildOopValidationContent(detailBySubmissionId.get(submission.getSubmissionId()))
                );

                try (InputStream in = fileStorageService.downloadFile(
                        minioConfig.getBucket().getSubmissions(),
                        submission.getFilePath())) {
                    addBinaryEntry(zos, rootFolder + "/submission/" + exportedFileName, in);
                } catch (Exception ex) {
                    log.error("[SubmissionBundleExport] Failed to add submission file '{}' (submissionId={}): {}",
                            submission.getFilePath(), submission.getSubmissionId(), ex.getMessage(), ex);
                    addTextEntry(
                            zos,
                            rootFolder + "/submission/" + gradingFolderName + "_missing_submission.txt",
                            buildMissingSubmissionContent(submission.getFileName(), submission.getFilePath(), ex)
                    );
                }
            }

            zos.finish();
            return bos.toByteArray();
        }
    }

    /**
     * Suggested downloaded zip file name.
     */
    private String buildMissingSubmissionContent(String submissionFileName, String submissionPath, Exception ex) {
        String safeName = submissionFileName == null || submissionFileName.isBlank()
                ? "Không rõ tên file"
                : submissionFileName;
        String safePath = submissionPath == null || submissionPath.isBlank()
                ? "Không rõ đường dẫn"
                : submissionPath;
        String safeReason = ex == null || ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Không rõ nguyên nhân"
                : ex.getMessage();

        return "Không thể tải file bài nộp từ storage.\n"
                + "Tên file: " + safeName + "\n"
                + "Đường dẫn: " + safePath + "\n"
                + "Lý do: " + safeReason + "\n"
                + "Hệ thống vẫn export phần oopvalidation cho bài này.";
    }

    public String submissionBundleFileName(String examName, String blockName) {
        String ts = LocalDateTime.now().format(TIMESTAMP_FMT);
        return sanitizeDownloadFileName(
                "DuLieuCham_"
                        + slugForDownloadName(examName, "exam")
                        + "_"
                        + slugForDownloadName(blockName, "block")
                        + "_"
                        + ts
                        + ".zip"
        );
    }

    private String buildOopValidationContent(GradingResultResponse detail) {
        List<AnswerGradingDetail> answers = detail != null && detail.getAnswers() != null
                ? new ArrayList<>(detail.getAnswers())
                : List.of();

        if (answers.isEmpty()) {
            return "Chưa có kết quả chấm bài.";
        }

        answers.sort(Comparator.comparingInt(AnswerGradingDetail::getQuestionNumber));

        List<String> sections = new ArrayList<>();
        for (AnswerGradingDetail answer : answers) {
            int questionNumber = answer.getQuestionNumber();
            BigDecimal oopScore = answer.getAiReview() != null ? answer.getAiReview().getOopScore() : null;
            String comment = answer.getAiReview() != null ? answer.getAiReview().getComment() : null;

            sections.add("Q" + questionNumber + ": " + formatScore(oopScore) + "\n" + cleanComment(comment));
        }

        return String.join("\n\n", sections);
    }

    private static String formatScore(BigDecimal value) {
        if (value == null) return "N/A";
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString();
    }

    private static String cleanComment(String comment) {
        if (comment == null) return "Không có AI Code Review.";

        String normalized = comment
                .replace("\r\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();

        return normalized.isBlank() ? "Không có AI Code Review." : normalized;
    }

    private static void addDirectoryEntry(ZipOutputStream zos, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        zos.closeEntry();
    }

    private static void addTextEntry(ZipOutputStream zos, String entryName, String content) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static void addBinaryEntry(ZipOutputStream zos, String entryName, InputStream in) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        in.transferTo(zos);
        zos.closeEntry();
    }

    private static String safeArchiveFileName(String rawFileName) {
        String fileName = rawFileName == null ? "submission.zip" : rawFileName;
        fileName = fileName.replace('\\', '/');
        int slashIndex = fileName.lastIndexOf('/');
        if (slashIndex >= 0) {
            fileName = fileName.substring(slashIndex + 1);
        }
        fileName = fileName
                .replaceAll("[\\p{Cntrl}]", "")
                .replace("..", ".")
                .trim();

        if (fileName.isBlank()) {
            return "submission.zip";
        }
        return fileName;
    }

    private static String allocateUniqueFileName(String originalFileName, Set<String> usedNames) {
        String ext = extensionOf(originalFileName);
        String base = stripExtension(originalFileName);
        String candidate = originalFileName;
        int counter = 2;

        while (usedNames.contains(candidate.toLowerCase(Locale.ROOT))) {
            candidate = base + "_" + counter + ext;
            counter += 1;
        }

        usedNames.add(candidate.toLowerCase(Locale.ROOT));
        return candidate;
    }

    private static String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex);
    }

    private static String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    private static String sanitizePathSegment(String value, String fallback) {
        String text = value == null ? "" : value;
        text = text
                .replace('/', '-')
                .replace('\\', '-')
                .replace(':', '-')
                .replace('*', '-')
                .replace('?', '-')
                .replace('"', '-')
                .replace('<', '-')
                .replace('>', '-')
                .replace('|', '-')
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();

        if (text.isBlank()) {
            return fallback;
        }
        return text;
    }

    private static String slugForDownloadName(String value, String fallback) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        text = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return text.isBlank() ? fallback : text;
    }

    private static String sanitizeDownloadFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]+", "_");
    }
}
