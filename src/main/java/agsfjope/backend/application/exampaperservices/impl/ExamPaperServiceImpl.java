package agsfjope.backend.application.exampaperservices.impl;

import agsfjope.backend.application.dtos.responses.exampaper.ExamPaperResponse;
import agsfjope.backend.application.dtos.responses.exampaper.QuestionResponse;
import agsfjope.backend.application.dtos.responses.exampaper.TestCaseResponse;
import agsfjope.backend.application.exampaperservices.ExamPaperService;
import agsfjope.backend.configuration.storage.MinioConfig;
import agsfjope.backend.core.entities.Block;
import agsfjope.backend.core.entities.ExamPaper;
import agsfjope.backend.core.entities.Question;
import agsfjope.backend.core.entities.TestCase;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.exceptions.exampaper.ExamPaperHasSubmissionsException;
import agsfjope.backend.core.exceptions.exampaper.InvalidZipStructureException;
import agsfjope.backend.core.repositories.block.BlockRepository;
import agsfjope.backend.core.repositories.exam.ExamRepository;
import agsfjope.backend.core.repositories.exampaper.ExamPaperRepository;
import agsfjope.backend.core.repositories.exampaper.QuestionRepository;
import agsfjope.backend.core.repositories.exampaper.TestCaseRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.application.ports.out.FileStoragePort;
import agsfjope.backend.infrastructure.storage.parser.ParsedExamPaper;
import agsfjope.backend.infrastructure.storage.parser.ZipExamPaperParser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link ExamPaperService}.
 *
 * <p>Business rules enforced:</p>
 * <ul>
 *   <li><strong>BR-09</strong>: 1 Block = 1 ExamPaper. Auto-overwrite on re-upload.</li>
 *   <li><strong>BR-10</strong>: Archive parsed automatically → Questions + TestCases.</li>
 *   <li><strong>BR-11</strong>: Cannot modify/delete if any submission exists for the block.</li>
 *   <li><strong>BR-16</strong>: Max file size = 20 MB.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamPaperServiceImpl implements ExamPaperService {

    private static final String CONFIG_KEY_MAX_EXAM_PAPER_MB = "MAX_EXAM_PAPER_MB";
    private static final int DEFAULT_MAX_EXAM_PAPER_MB = 20;
    private static final int  DEFAULT_TIME_LIMIT_MS = 5000;

    private final ExamRepository       examRepository;
    private final BlockRepository      blockRepository;
    private final ExamPaperRepository  examPaperRepository;
    private final QuestionRepository   questionRepository;
    private final TestCaseRepository   testCaseRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository       userRepository;
    private final SystemConfigRepository systemConfigRepository;

    private final FileStoragePort        minioService;
    private final MinioConfig          minioConfig;
    private final ZipExamPaperParser   parser;

    // ─── UPLOAD ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ExamPaperResponse upload(UUID examId, UUID blockId, UUID staffId, MultipartFile file, String examCode) {
        log.info("ExamPaperService.upload: examId={}, blockId={}, staffId={}, file={}, examCode={}",
                examId, blockId, staffId, file.getOriginalFilename(), examCode);

        // ── 0. Validate examCode (bắt buộc) ─────────────────────────────────
        if (examCode == null || examCode.isBlank()) {
            throw new IllegalArgumentException("Mã đề thi là bắt buộc. Vui lòng nhập mã đề trước khi upload.");
        }
        String trimmedExamCode = examCode.trim();

        // ── 1. Validate exam exists ───────────────────────────────────────────
        if (!examRepository.existsById(examId)) {
            throw new NotFoundException("Không tìm thấy kỳ thi với ID: " + examId);
        }

        // ── 2. Validate block exists and belongs to this exam ─────────────────
        Block block = blockRepository.findByBlockId(blockId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy block với ID: " + blockId));

        if (!block.getExam().getExamId().equals(examId)) {
            throw new IllegalArgumentException(
                    "Block " + blockId + " không thuộc kỳ thi " + examId + ".");
        }

        // ── 3. BR-11: Reject if any submission already exists ─────────────────
        if (submissionRepository.existsByBlock_BlockId(blockId)) {
            throw new ExamPaperHasSubmissionsException(
                    "Không thể upload đề thi: Block này đã có sinh viên nộp bài. " +
                    "Không được thay đổi đề thi sau khi đã có submission (BR-11).");
        }

        // ── 4. BR-16: Validate file size ──────────────────────────────────────
        long fileSize = file.getSize();
        if (fileSize == 0) {
            throw new IllegalStateException("File upload rỗng — vui lòng chọn file hợp lệ.");
        }
        int maxUploadMb = resolveMaxExamPaperMb();
        long maxFileSizeBytes = maxUploadMb * 1024L * 1024L;
        if (fileSize > maxFileSizeBytes) {
            throw new IllegalStateException(String.format(
                "File quá lớn: %.1f MB. Kích thước tối đa cho phép là %d MB (BR-16).",
                fileSize / (1024.0 * 1024.0),
                maxUploadMb));
        }

        // ── 5. Validate file extension ────────────────────────────────────────
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalStateException("Tên file không hợp lệ.");
        }
        String lowerName = originalFilename.toLowerCase();
        if (!lowerName.endsWith(".zip") && !lowerName.endsWith(".rar")) {
            throw new IllegalStateException(
                    "Định dạng file không được hỗ trợ: '" + originalFilename +
                    "'. Chỉ chấp nhận file .zip hoặc .rar.");
        }
        String extension = lowerName.endsWith(".zip") ? ".zip" : ".rar";

        // ── 6. BR-09: Prepare overwrite info (do NOT delete old paper yet) ────
        // Safety fix: parse/upload new file first. If parse/upload fails, old paper remains intact.
        ExamPaper existingPaper = examPaperRepository.findByBlock_BlockId(blockId).orElse(null);

        // ── 7. Parse the archive ──────────────────────────────────────────────
        ParsedExamPaper parsedExamPaper = parseArchive(file, extension, originalFilename);

        // ── 8. Upload raw archive to MinIO ────────────────────────────────────
        String objectPath = buildObjectPath(block.getExam(), block, originalFilename);
        String contentType = extension.equals(".zip")
                ? "application/zip"
                : "application/x-rar-compressed";

        try {
            minioService.uploadFile(
                    minioConfig.getBucket().getExamPapers(),
                    objectPath,
                    file.getInputStream(),
                    contentType,
                    fileSize
            );
        } catch (IOException e) {
            throw new RuntimeException("Không thể đọc file upload: " + e.getMessage(), e);
        }
        log.info("ExamPaperService: Uploaded archive to MinIO: {}", objectPath);

        // ── 8.5 BR-09: Delete old data only after new archive upload succeeded ─
        if (existingPaper != null) {
            log.info("ExamPaperService: Found existing exam paper {} for block {}. Overwriting (BR-09).",
                existingPaper.getExamPaperId(), blockId);
            // If old path equals new path, skip MinIO delete to avoid removing the newly uploaded file.
            overwriteOldPaper(existingPaper, objectPath);
            // Flush immediately so the DELETE is sent to DB before the INSERT below,
            // otherwise Hibernate batches them and the UNIQUE constraint fires first.
            examPaperRepository.flush();
        }

        // ── 9. Persist ExamPaper entity ───────────────────────────────────────
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy user với ID: " + staffId));

        int totalQuestions = parsedExamPaper.questions().size();
        int totalTestCases = parsedExamPaper.totalTestCases();

        ExamPaper savedPaper = examPaperRepository.save(
                ExamPaper.builder()
                        .block(block)
                        .uploadedBy(staff)
                        .fileName(originalFilename)
                        .examCode(trimmedExamCode)
                        .filePath(objectPath)
                        .fileSizeBytes(fileSize)
                        .totalQuestions(totalQuestions)
                        .totalTestCases(totalTestCases)
                        .build()
        );

        // ── 10. Persist Questions and TestCases ───────────────────────────────
        List<Question> savedQuestions = persistQuestionsAndTestCases(parsedExamPaper, savedPaper);

        log.info("ExamPaperService: Successfully uploaded exam paper {} with {} questions, {} test cases.",
                savedPaper.getExamPaperId(), totalQuestions, totalTestCases);

        return toResponse(savedPaper, savedQuestions);
    }

    // ─── GET ─────────────────────────────────────────────────────────────────

    @Override
    public ExamPaperResponse getByBlock(UUID examId, UUID blockId) {
        validateBlockOwnership(examId, blockId);

        ExamPaper paper = examPaperRepository.findByBlock_BlockId(blockId)
                .orElseThrow(() -> new NotFoundException(
                        "Block " + blockId + " chưa có đề thi được upload."));

        List<Question> questions = questionRepository
                .findByExamPaper_ExamPaperIdOrderByQuestionNumberAsc(paper.getExamPaperId());

        return toResponse(paper, questions);
    }

    // ─── DELETE ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteByBlock(UUID examId, UUID blockId) {
        log.info("ExamPaperService.deleteByBlock: examId={}, blockId={}", examId, blockId);

        validateBlockOwnership(examId, blockId);

        ExamPaper paper = examPaperRepository.findByBlock_BlockId(blockId)
                .orElseThrow(() -> new NotFoundException(
                        "Block " + blockId + " chưa có đề thi để xóa."));

        // BR-11: cannot delete if submissions exist
        if (submissionRepository.existsByBlock_BlockId(blockId)) {
            throw new ExamPaperHasSubmissionsException(
                    "Không thể xóa đề thi: Block này đã có sinh viên nộp bài (BR-11).");
        }

        overwriteOldPaper(paper, null);
        log.info("ExamPaperService: Deleted exam paper {} for block {}.", paper.getExamPaperId(), blockId);
    }

    // ─── DOWNLOAD ────────────────────────────────────────────────────────────

    @Override
    public InputStream downloadByBlock(UUID examId, UUID blockId) {
        validateBlockOwnership(examId, blockId);

        ExamPaper paper = examPaperRepository.findByBlock_BlockId(blockId)
                .orElseThrow(() -> new NotFoundException(
                        "Block " + blockId + " chưa có đề thi để download."));

        return minioService.downloadFile(
                minioConfig.getBucket().getExamPapers(),
                paper.getFilePath()
        );
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────

    /**
     * Validates that the exam exists and the block belongs to it.
     */
    private void validateBlockOwnership(UUID examId, UUID blockId) {
        if (!examRepository.existsById(examId)) {
            throw new NotFoundException("Không tìm thấy kỳ thi với ID: " + examId);
        }
        Block block = blockRepository.findByBlockId(blockId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy block với ID: " + blockId));
        if (!block.getExam().getExamId().equals(examId)) {
            throw new IllegalArgumentException(
                    "Block " + blockId + " không thuộc kỳ thi " + examId + ".");
        }
    }

    /**
     * Deletes an old exam paper: TestCases → Questions → ExamPaper (DB) + MinIO file.
     * Order matters to avoid FK constraint violations.
     */
    private void overwriteOldPaper(ExamPaper old, String preservedObjectPath) {
        UUID oldId = old.getExamPaperId();
        // Delete in FK order: TestCases first, then Questions, then ExamPaper
        testCaseRepository.deleteByQuestion_ExamPaper_ExamPaperId(oldId);
        questionRepository.deleteByExamPaper_ExamPaperId(oldId);
        examPaperRepository.deleteById(oldId);

        // Delete from MinIO, except when old path == new path (new file already uploaded there)
        if (preservedObjectPath != null && preservedObjectPath.equals(old.getFilePath())) {
            log.info("ExamPaperService: Keep MinIO object '{}' because it was replaced by new upload.",
                    old.getFilePath());
        } else {
            try {
                minioService.deleteFile(minioConfig.getBucket().getExamPapers(), old.getFilePath());
            } catch (Exception e) {
                // Log but don't fail — the DB record is already gone
                log.warn("ExamPaperService: Could not delete old MinIO file '{}': {}", old.getFilePath(), e.getMessage());
            }
        }
    }

    /**
     * Saves to a temp file, parses the archive, cleans up temp file.
     */
    private ParsedExamPaper parseArchive(MultipartFile file, String extension, String originalFilename) {
        Path tmpFile = null;
        try {
            tmpFile = Files.createTempFile("exam-paper-upload-", extension);
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return parser.parseFromTempFile(tmpFile, extension);

        } catch (InvalidZipStructureException e) {
            throw e; // Re-throw — already a proper domain exception
        } catch (IOException e) {
            throw new InvalidZipStructureException(
                    "Không thể đọc file '" + originalFilename + "': " + e.getMessage(), e);
        } finally {
            if (tmpFile != null) {
                try { Files.deleteIfExists(tmpFile); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Persists all questions and their test cases to the database.
     * Test case scores are distributed with 2-decimal precision while guaranteeing
     * the total equals question maxScore exactly.
     *
     * @return ordered list of saved Question entities (with IDs populated)
     */
    private List<Question> persistQuestionsAndTestCases(ParsedExamPaper parsed, ExamPaper savedPaper) {
        List<Question> savedQuestions = new ArrayList<>();

        for (ParsedExamPaper.ParsedQuestion pq : parsed.questions()) {
            Question q = questionRepository.save(
                    Question.builder()
                            .examPaper(savedPaper)
                            .questionNumber(pq.questionNumber())
                            .title(pq.title())
                            .description(pq.description())
                            .maxScore(pq.maxScore())
                            .removeSpaces(pq.removeSpaces())
                            .caseSensitive(pq.caseSensitive())
                            .build()
            );

            int totalTCs = pq.testCases().size();
            List<BigDecimal> distributedScores = distributeTestCaseScores(pq.maxScore(), totalTCs);

            for (int i = 0; i < pq.testCases().size(); i++) {
                ParsedExamPaper.ParsedTestCase ptc = pq.testCases().get(i);
                testCaseRepository.save(
                        TestCase.builder()
                                .question(q)
                                .testCaseNumber(ptc.testCaseNumber())
                                .inputData(ptc.inputData())
                                .expectedOutput(ptc.expectedOutput())
                                .score(distributedScores.get(i))
                                .timeLimitMs(DEFAULT_TIME_LIMIT_MS)
                                .build()
                );
            }
            savedQuestions.add(q);
        }
        return savedQuestions;
    }

    /**
     * Distributes question max score across test cases while guaranteeing:
     * <ul>
     *   <li>Every test case score has scale 2.</li>
     *   <li>Total sum equals question max score exactly.</li>
     * </ul>
     *
     * <p>Strategy: assign base score (ROUND_DOWN) for the first N-1 test cases,
     * and put the remainder into the last test case.</p>
     */
    private List<BigDecimal> distributeTestCaseScores(BigDecimal maxScore, int totalTCs) {
        if (totalTCs <= 0) {
            throw new IllegalStateException("Số lượng test case phải lớn hơn 0 để chia điểm.");
        }

        if (totalTCs == 1) {
            return List.of(maxScore.setScale(2, RoundingMode.HALF_UP));
        }

        BigDecimal base = maxScore.divide(BigDecimal.valueOf(totalTCs), 2, RoundingMode.DOWN);
        List<BigDecimal> scores = new ArrayList<>(totalTCs);

        for (int i = 0; i < totalTCs - 1; i++) {
            scores.add(base);
        }

        BigDecimal allocated = base.multiply(BigDecimal.valueOf(totalTCs - 1L));
        BigDecimal lastScore = maxScore.subtract(allocated).setScale(2, RoundingMode.HALF_UP);
        scores.add(lastScore);

        return scores;
    }

    /**
     * Builds the MinIO object path for an exam paper archive.
     * Format: {@code exam-papers/{Semester}-{AcademicYear}/{BlockName}/{fileName}}
     * Example: {@code exam-papers/Spring-2025/Block 10/DeThi.zip}
     */
    private String buildObjectPath(agsfjope.backend.core.entities.Exam exam,
                                   agsfjope.backend.core.entities.Block block,
                                   String fileName) {
        String semester = expandSemester(exam.getSemester());
        String folder   = semester + "-" + exam.getAcademicYear();
        return sanitize(folder) + "/" + sanitize(block.getName()) + "/" + fileName;
    }

    /**
     * Expands short semester code to full English name.
     * SP → Spring, SU → Summer, FA → Fall. Unknown codes are kept as-is.
     */
    private String expandSemester(String code) {
        if (code == null) return "Unknown";
        return switch (code.toUpperCase()) {
            case "SP" -> "Spring";
            case "SU" -> "Summer";
            case "FA" -> "Fall";
            default   -> code;
        };
    }

    /**
     * Normalizes a string for safe use in a Supabase Storage object path.
     * <p>
     * AWS SDK v2 reject Unicode trong object key nên cần chuyển về ASCII trước.
     * Bước 1: NFD decompose → tách dấu ra khỏi chữ cái
     * Bước 2: Strip combining diacritics
     * Bước 3: Xử lý Đ/đ (không decompose được bằng NFD)
     * Bước 4: Thay các kí tự ngưi hiểm bằng underscore
     * </p>
     */
    private String sanitize(String value) {
        if (value == null) return "unknown";
        String normalized = java.text.Normalizer
                .normalize(value.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        normalized = normalized.replace("Đ", "D").replace("đ", "d");
        return normalized.replaceAll("[^a-zA-Z0-9\\-_. ]", "_");
    }

    /**
     * Reads max upload size (MB) from SystemConfigs with key {@code MAX_EXAM_PAPER_MB}.
     * Falls back to 20 MB if missing, invalid, or non-positive.
     */
    private int resolveMaxExamPaperMb() {
        try {
            return systemConfigRepository.findByConfigKey(CONFIG_KEY_MAX_EXAM_PAPER_MB)
                    .map(cfg -> {
                        try {
                            int value = Integer.parseInt(cfg.getConfigValue().trim());
                            if (value <= 0) {
                                log.warn("{} must be > 0. Found '{}'. Use default {} MB.",
                                        CONFIG_KEY_MAX_EXAM_PAPER_MB, cfg.getConfigValue(), DEFAULT_MAX_EXAM_PAPER_MB);
                                return DEFAULT_MAX_EXAM_PAPER_MB;
                            }
                            return value;
                        } catch (NumberFormatException e) {
                            log.warn("Invalid {} value '{}'. Use default {} MB.",
                                    CONFIG_KEY_MAX_EXAM_PAPER_MB, cfg.getConfigValue(), DEFAULT_MAX_EXAM_PAPER_MB);
                            return DEFAULT_MAX_EXAM_PAPER_MB;
                        }
                    })
                    .orElse(DEFAULT_MAX_EXAM_PAPER_MB);
        } catch (Exception e) {
            log.warn("Could not read {} from SystemConfigs. Use default {} MB. Error: {}",
                    CONFIG_KEY_MAX_EXAM_PAPER_MB, DEFAULT_MAX_EXAM_PAPER_MB, e.getMessage());
            return DEFAULT_MAX_EXAM_PAPER_MB;
        }
    }

    // ─── Mappers ─────────────────────────────────────────────────────────────

    private ExamPaperResponse toResponse(ExamPaper paper, List<Question> questions) {
        List<QuestionResponse> questionResponses = questions.stream()
                .map(q -> {
                    List<TestCase> testCases = testCaseRepository
                            .findByQuestion_QuestionIdOrderByTestCaseNumberAsc(q.getQuestionId());
                    return toQuestionResponse(q, testCases);
                })
                .toList();

        return ExamPaperResponse.builder()
                .examPaperId(paper.getExamPaperId())
                .blockId(paper.getBlock().getBlockId())
                .blockName(paper.getBlock().getName())
                .fileName(paper.getFileName())
                .examCode(paper.getExamCode())
                .fileSizeBytes(paper.getFileSizeBytes())
                .totalQuestions(paper.getTotalQuestions())
                .totalTestCases(paper.getTotalTestCases())
                .uploadedAt(paper.getUploadedAt())
                .questions(questionResponses)
                .build();
    }

    private QuestionResponse toQuestionResponse(Question q, List<TestCase> testCases) {
        return QuestionResponse.builder()
                .questionId(q.getQuestionId())
                .questionNumber(q.getQuestionNumber())
                .title(q.getTitle())
                .description(q.getDescription())
                .maxScore(q.getMaxScore())
                .totalTestCases(testCases.size())
                .removeSpaces(q.getRemoveSpaces())
                .caseSensitive(q.getCaseSensitive())
                .testCases(testCases.stream().map(this::toTestCaseResponse).toList())
                .build();
    }

    private TestCaseResponse toTestCaseResponse(TestCase tc) {
        return TestCaseResponse.builder()
                .testCaseId(tc.getTestCaseId())
                .testCaseNumber(tc.getTestCaseNumber())
                .inputData(tc.getInputData())
                .expectedOutput(tc.getExpectedOutput())
                .score(tc.getScore())
                .timeLimitMs(tc.getTimeLimitMs())
                .build();
    }
}
