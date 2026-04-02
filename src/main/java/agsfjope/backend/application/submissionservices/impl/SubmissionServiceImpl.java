package agsfjope.backend.application.submissionservices.impl;

import agsfjope.backend.application.dtos.responses.submission.AnswerResponse;
import agsfjope.backend.application.dtos.responses.submission.SubmissionResponse;
import agsfjope.backend.application.submissionservices.SubmissionService;
import agsfjope.backend.configuration.storage.MinioConfig;
import agsfjope.backend.core.entities.Answer;
import agsfjope.backend.core.entities.Block;
import agsfjope.backend.core.entities.Question;
import agsfjope.backend.core.entities.Submission;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.ExamStatus;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.exceptions.exampaper.InvalidZipStructureException;
import agsfjope.backend.core.exceptions.submission.ExamNotOngoingException;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.block.BlockRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.core.repositories.exam.ExamRepository;
import agsfjope.backend.core.repositories.exampaper.ExamPaperRepository;
import agsfjope.backend.core.repositories.exampaper.QuestionRepository;
import agsfjope.backend.core.repositories.submission.AnswerRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import agsfjope.backend.infrastructure.storage.MinioService;
import agsfjope.backend.infrastructure.storage.parser.ParsedSubmission;
import agsfjope.backend.infrastructure.storage.parser.SubmissionZipParser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link SubmissionService}.
 *
 * <p>Business rules enforced:</p>
 * <ul>
 *   <li><strong>BR-14</strong> — Exam must be ONGOING to accept submissions.</li>
 *   <li><strong>BR-15</strong> — Archive structure: {@code {n}/run/*.jar} + {@code {n}/src/*.java}.</li>
 *   <li><strong>BR-16</strong> — File size limited by {@code MAX_UPLOAD_SIZE_MB} SystemConfig (fallback 50 MB).</li>
 *   <li><strong>BR-17</strong> — Resubmit fully overwrites prior submission.</li>
 *   <li><strong>BR-18</strong> — One active submission per student per block.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private static final long   DEFAULT_MAX_SIZE_BYTES = 50L * 1024 * 1024; // 50 MB fallback
    private static final String CONFIG_KEY_MAX_UPLOAD  = "MAX_UPLOAD_SIZE_MB";

    private final ExamRepository       examRepository;
    private final BlockRepository      blockRepository;
    private final ExamPaperRepository  examPaperRepository;
    private final QuestionRepository   questionRepository;
    private final SubmissionRepository submissionRepository;
    private final AnswerRepository     answerRepository;
    private final UserRepository       userRepository;
    private final SystemConfigRepository systemConfigRepository;

    private final MinioService         minioService;
    private final MinioConfig          minioConfig;
    private final SubmissionZipParser  parser;

    // ─── SUBMIT ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SubmissionResponse submit(UUID examId, UUID blockId, UUID studentId, MultipartFile file) {
        log.info("SubmissionService.submit: examId={}, blockId={}, studentId={}, file={}",
                examId, blockId, studentId, file.getOriginalFilename());

        // ── 1. Validate exam exists ───────────────────────────────────────────
        if (!examRepository.existsById(examId)) {
            throw new NotFoundException("Không tìm thấy kỳ thi với ID: " + examId);
        }

        // ── 2. Validate block belongs to exam ────────────────────────────────
        Block block = blockRepository.findByBlockId(blockId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy block với ID: " + blockId));

        if (!block.getExam().getExamId().equals(examId)) {
            throw new IllegalArgumentException(
                    "Block " + blockId + " không thuộc kỳ thi " + examId + ".");
        }

        // ── 3. BR-14: Block must be ONGOING (check by block times) ──────────────
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        if (block.getStartTime() != null && now.isBefore(block.getStartTime())) {
            throw new ExamNotOngoingException(
                    "Ca thi \"" + block.getName() + "\" của kỳ thi \"" + block.getExam().getName() +
                    "\" chưa bắt đầu. Ca thi sẽ mở lúc " + block.getStartTime() + " (MSG-40).");
        }
        if (block.getEndTime() != null && now.isAfter(block.getEndTime())) {
            throw new ExamNotOngoingException(
                    "Ca thi \"" + block.getName() + "\" của kỳ thi \"" + block.getExam().getName() +
                    "\" đã kết thúc lúc " + block.getEndTime() + " (MSG-34). Không thể nộp bài.");
        }

        // ── 4. Validate block has an exam paper ──────────────────────────────
        if (examPaperRepository.findByBlock_BlockId(blockId).isEmpty()) {
            throw new NotFoundException(
                    "Block " + blockId + " chưa có đề thi được upload. Không thể nộp bài.");
        }

        // ── 5. BR-16: Validate file size and extension ───────────────────────
        long maxSizeBytes = resolveMaxUploadSizeBytes();
        long fileSize = file.getSize();
        if (fileSize == 0) {
            throw new IllegalStateException("File upload rỗng — vui lòng chọn file hợp lệ.");
        }
        if (fileSize > maxSizeBytes) {
            throw new IllegalStateException(String.format(
                    "File quá lớn: %.1f MB. Kích thước tối đa cho phép là %.0f MB (BR-16).",
                    fileSize / (1024.0 * 1024.0),
                    maxSizeBytes / (1024.0 * 1024.0)));
        }

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

        // ── 6. Parse the archive ──────────────────────────────────────────────
        ParsedSubmission parsed = parseArchive(file, extension, originalFilename);

        // ── 7. BR-17: If prior submission exists → delete it ─────────────────
        boolean isResubmit = false;
        Optional<Submission> existing =
                submissionRepository.findByStudent_UserIdAndBlock_BlockId(studentId, blockId);
        if (existing.isPresent()) {
            deleteOldSubmission(existing.get());
            submissionRepository.flush(); // Ép Hibernate flush DELETE trước INSERT mới (tránh uq_studentblock violation)
            isResubmit = true;
            log.info("SubmissionService: Resubmit detected — deleted old submission {} (BR-17).",
                    existing.get().getSubmissionId());
        }

        // ── 8. Upload archive to MinIO ────────────────────────────────────────
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sinh viên với ID: " + studentId));

        String objectPath = buildObjectPath(block.getExam(), block, student, originalFilename);
        String contentType = extension.equals(".zip")
                ? "application/zip"
                : "application/x-rar-compressed";

        try {
            minioService.uploadFile(
                    minioConfig.getBucket().getSubmissions(),
                    objectPath,
                    file.getInputStream(),
                    contentType,
                    fileSize
            );
        } catch (IOException e) {
            throw new RuntimeException("Không thể đọc file upload: " + e.getMessage(), e);
        }
        log.info("SubmissionService: Uploaded submission to MinIO: {}", objectPath);

        // ── 9. Persist Submission entity ──────────────────────────────────────
        Submission saved = submissionRepository.save(
                Submission.builder()
                        .student(student)
                        .block(block)
                        .fileName(originalFilename)
                        .filePath(objectPath)
                        .fileSizeBytes(fileSize)
                        .build()
        );

        // ── 10. Parse answers → map to Questions → persist Answers ────────────
        List<Question> questions = questionRepository
                .findByExamPaper_Block_BlockIdOrderByQuestionNumberAsc(blockId);

        List<Answer> answers = buildAnswers(saved, parsed, questions);
        answerRepository.saveAll(answers);

        log.info("SubmissionService: Submission {} saved with {} answers (resubmit={}).",
                saved.getSubmissionId(), answers.size(), isResubmit);

        return toResponse(saved, answers, questions, isResubmit);
    }

    // ─── GET MY SUBMISSION ────────────────────────────────────────────────────

    @Override
    public SubmissionResponse getMySubmission(UUID examId, UUID blockId, UUID studentId) {
        validateBlockOwnership(examId, blockId);

        Submission submission =
                submissionRepository.findByStudent_UserIdAndBlock_BlockId(studentId, blockId)
                .orElseThrow(() -> new NotFoundException(
                        "Bạn chưa nộp bài cho block này."));

        List<Answer> answers =
                answerRepository.findBySubmission_SubmissionIdOrderByQuestion_QuestionNumberAsc(
                        submission.getSubmissionId());

        List<Question> questions = questionRepository
                .findByExamPaper_Block_BlockIdOrderByQuestionNumberAsc(blockId);

        return toResponse(submission, answers, questions, false);
    }

    // ─── DOWNLOAD ────────────────────────────────────────────────────────────

    @Override
    public InputStream downloadMySubmission(UUID examId, UUID blockId, UUID studentId) {
        validateBlockOwnership(examId, blockId);

        Submission submission =
                submissionRepository.findByStudent_UserIdAndBlock_BlockId(studentId, blockId)
                .orElseThrow(() -> new NotFoundException(
                        "Bạn chưa nộp bài cho block này."));

        return minioService.downloadFile(
                minioConfig.getBucket().getSubmissions(),
                submission.getFilePath()
        );
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────

    private long resolveMaxUploadSizeBytes() {
        return systemConfigRepository.findByConfigKey(CONFIG_KEY_MAX_UPLOAD)
                .map(cfg -> {
                    try {
                        return Long.parseLong(cfg.getConfigValue()) * 1024L * 1024L;
                    } catch (NumberFormatException e) {
                        log.warn("SubmissionService: Cannot parse MAX_UPLOAD_SIZE_MB={}, using fallback 50MB.",
                                cfg.getConfigValue());
                        return DEFAULT_MAX_SIZE_BYTES;
                    }
                })
                .orElseGet(() -> {
                    log.warn("SubmissionService: MAX_UPLOAD_SIZE_MB config not found, using fallback 50MB.");
                    return DEFAULT_MAX_SIZE_BYTES;
                });
    }

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

    private void deleteOldSubmission(Submission old) {
        answerRepository.deleteBySubmission_SubmissionId(old.getSubmissionId());
        submissionRepository.deleteById(old.getSubmissionId());
        try {
            minioService.deleteFile(minioConfig.getBucket().getSubmissions(), old.getFilePath());
        } catch (Exception e) {
            log.warn("SubmissionService: Could not delete old MinIO file '{}': {}",
                    old.getFilePath(), e.getMessage());
        }
    }

    private ParsedSubmission parseArchive(MultipartFile file, String extension, String originalFilename) {
        Path tmpFile = null;
        try {
            tmpFile = Files.createTempFile("submission-upload-", extension);
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return parser.parseFromTempFile(tmpFile, extension);
        } catch (InvalidZipStructureException e) {
            throw e;
        } catch (IOException e) {
            throw new InvalidZipStructureException(
                    "Không thể đọc file '" + originalFilename + "': " + e.getMessage(), e);
        } finally {
            if (tmpFile != null) {
                try { Files.deleteIfExists(tmpFile); } catch (IOException ignored) {}
            }
        }
    }

    private List<Answer> buildAnswers(
            Submission submission,
            ParsedSubmission parsed,
            List<Question> questions) {

        Map<Integer, ParsedSubmission.ParsedAnswer> parsedMap = parsed.answers().stream()
                .collect(Collectors.toMap(ParsedSubmission.ParsedAnswer::questionNumber, a -> a));

        List<Answer> answers = new ArrayList<>();
        for (Question question : questions) {
            int qNum = question.getQuestionNumber();
            ParsedSubmission.ParsedAnswer pa = parsedMap.get(qNum);

            String jarPath = null;
            String srcPath = null;

            if (pa != null) {
                jarPath = pa.jarEntryPath();
                srcPath = pa.hasSource() ? qNum + "/src/" : null;
            }

            answers.add(Answer.builder()
                    .submission(submission)
                    .question(question)
                    .jarFilePath(jarPath)
                    .sourceCodePath(srcPath)
                    .build());
        }
        return answers;
    }

    /**
     * Builds the MinIO object path for a student submission archive.
     * Format: {@code submissions/{Semester}-{AcademicYear}/{BlockName}/{FullName} - {MSSV}/{fileName}}
     * Example: {@code submissions/Spring-2025/Block 10/Nguyen Van A - SE12345/BaiNop.zip}
     */
    private String buildObjectPath(agsfjope.backend.core.entities.Exam exam,
                                   agsfjope.backend.core.entities.Block block,
                                   agsfjope.backend.core.entities.User student,
                                   String fileName) {
        String semester   = expandSemester(exam.getSemester());
        String folder     = semester + "-" + exam.getAcademicYear();
        String studentDir = student.getFullName()
                + (student.getMssv() != null ? " - " + student.getMssv() : "");
        return "submissions/"
                + sanitize(folder) + "/"
                + sanitize(block.getName()) + "/"
                + sanitize(studentDir) + "/"
                + fileName;
    }

    /**
     * Expands short semester code to full English name.
     * SP → Spring, SU → Summer, FA → Fall.
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
     * Sanitizes a string for safe use in a MinIO object path.
     */
    private String sanitize(String value) {
        if (value == null) return "unknown";
        return value.trim().replaceAll("[^a-zA-Z0-9\\-_. ]", "_");
    }

    private SubmissionResponse toResponse(
            Submission submission,
            List<Answer> answers,
            List<Question> questions,
            boolean isResubmit) {

        Map<Integer, String> qTitleMap = questions.stream()
                .collect(Collectors.toMap(Question::getQuestionNumber, Question::getTitle));

        List<AnswerResponse> answerResponses = answers.stream()
                .map(a -> toAnswerResponse(a, qTitleMap))
                .toList();

        return SubmissionResponse.builder()
                .submissionId(submission.getSubmissionId())
                .blockId(submission.getBlock().getBlockId())
                .blockName(submission.getBlock().getName())
                .examName(submission.getBlock().getExam().getName())
                .fileName(submission.getFileName())
                .fileSizeBytes(submission.getFileSizeBytes() != null ? submission.getFileSizeBytes() : 0L)
                .status(submission.getStatus())
                .submittedAt(submission.getSubmittedAt())
                .totalAnswers(answerResponses.size())
                .resubmit(isResubmit)
                .answers(answerResponses)
                .build();
    }

    private AnswerResponse toAnswerResponse(Answer answer, Map<Integer, String> qTitleMap) {
        int qNum = answer.getQuestion().getQuestionNumber();
        boolean hasJar    = answer.getJarFilePath() != null;
        boolean hasSource = answer.getSourceCodePath() != null;

        String warning = null;
        if (!hasJar && !hasSource) {
            warning = "Sinh viên không có bài nộp cho câu hỏi này";
        } else if (!hasJar) {
            warning = "Sinh viên nộp bài không có file compile (.jar) cho câu này";
        }

        return AnswerResponse.builder()
                .answerId(answer.getAnswerId())
                .questionNumber(qNum)
                .questionTitle(qTitleMap.getOrDefault(qNum, "Câu " + qNum))
                .hasJar(hasJar)
                .hasSource(hasSource)
                .warningMessage(warning)
                .build();
    }
}
