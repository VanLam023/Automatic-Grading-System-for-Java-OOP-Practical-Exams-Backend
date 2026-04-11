package agsfjope.backend.infrastructure.storage;

import agsfjope.backend.configuration.storage.MinioConfig;
import agsfjope.backend.core.entities.Answer;
import agsfjope.backend.core.entities.ExamPaper;
import agsfjope.backend.core.entities.Submission;
import agsfjope.backend.core.repositories.exampaper.ExamPaperRepository;
import agsfjope.backend.core.repositories.submission.AnswerRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import agsfjope.backend.infrastructure.storage.parser.ParsedSubmission;
import agsfjope.backend.infrastructure.storage.parser.SubmissionZipParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Migration service để chuyển MinIO object paths từ format cũ (UUID-based)
 * sang format mới (human-readable).
 *
 * <h2>Format cũ → mới:</h2>
 * <pre>
 * ExamPaper:
 *   OLD: exam-papers/exams/{examId}/blocks/{blockId}/{fileName}
 *   NEW: exam-papers/{Semester}-{AcademicYear}/{BlockName}/{fileName}
 *
 * Submission:
 *   OLD: submissions/exams/{examId}/blocks/{blockId}/students/{studentId}/{fileName}
 *   NEW: submissions/{Semester}-{AcademicYear}/{BlockName}/{FullName} - {MSSV}/{fileName}
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioPathMigrationService {

    // Old format: key trong MinIO bắt đầu bằng "exams/" (không có bucket prefix)
    // Cả exam-papers và submissions đều dùng "exams/" prefix trong key
    private static final String OLD_PREFIX = "exams/";

    private final ExamPaperRepository  examPaperRepository;
    private final SubmissionRepository submissionRepository;
    private final AnswerRepository     answerRepository;
    private final MinioService         minioService;
    private final MinioConfig          minioConfig;
    private final SubmissionZipParser  submissionZipParser;

    // ─── Public records ───────────────────────────────────────────────────────

    public record MigrationReport(
            int examPapersMigrated,
            int examPapersSkipped,
            int examPapersFailed,
            int submissionsMigrated,
            int submissionsSkipped,
            int submissionsFailed,
            List<String> errors
    ) {}

    public record BackfillReport(
            int submissionsScanned,
            int answersUpdated,
            int answersSkipped,
            int failed,
            List<String> errors
    ) {}

    // ─── CASE 1: DB đã path mới, MinIO vẫn còn path cũ ──────────────────────

    /**
     * Dùng khi DB đã có path mới nhưng MinIO vẫn còn file ở path cũ (UUID-based).
     * Reconstruct old path từ entity UUID, check MinIO, copy → delete old.
     * Không update DB (DB đã đúng rồi).
     *
     * @param dryRun true = chỉ log, không thực sự copy/xóa
     */
    public MigrationReport fixMinioObjects(boolean dryRun) {
        log.warn("=== MinIO Fix Objects START (dryRun={}) ===", dryRun);
        List<String> errors = new ArrayList<>();

        // ── Fix exam papers ───────────────────────────────────────────────────
        List<ExamPaper> papers = examPaperRepository.findAll();
        int epMigrated = 0, epSkipped = 0, epFailed = 0;

        for (ExamPaper paper : papers) {
            String newPath = paper.getFilePath();
            if (newPath == null) { epSkipped++; continue; }

            var block = paper.getBlock();
            var exam  = block.getExam();
            // Reconstruct old MinIO object key from entity UUIDs
            String oldPath = "exam-papers/exams/"
                    + exam.getExamId() + "/blocks/"
                    + block.getBlockId() + "/"
                    + paper.getFileName();

            log.warn("[MINIO-FIX] ExamPaper {}: bucket='{}' | oldPath='{}' | newPath='{}'",
                    paper.getExamPaperId(),
                    minioConfig.getBucket().getExamPapers(), oldPath, newPath);

            try {
                boolean oldExists = minioService.fileExists(minioConfig.getBucket().getExamPapers(), oldPath);
                boolean newExists = minioService.fileExists(minioConfig.getBucket().getExamPapers(), newPath);
                log.warn("[MINIO-FIX] ExamPaper {}: oldExists={} newExists={}", paper.getExamPaperId(), oldExists, newExists);

                if (!oldExists) {
                    epSkipped++;
                    continue;
                }

                log.warn("ExamPaper {}: will copy '{}' → '{}'", paper.getExamPaperId(), oldPath, newPath);
                if (!dryRun) {
                    minioService.copyObject(minioConfig.getBucket().getExamPapers(), oldPath, newPath);
                    minioService.deleteFile(minioConfig.getBucket().getExamPapers(), oldPath);
                }
                epMigrated++;
            } catch (Exception e) {
                String msg = "ExamPaper " + paper.getExamPaperId() + ": " + e.getMessage();
                log.error("Fix FAILED: {}", msg, e);
                errors.add(msg);
                epFailed++;
            }
        }

        // ── Fix submissions ───────────────────────────────────────────────────
        List<Submission> submissions = submissionRepository.findAll();
        int subMigrated = 0, subSkipped = 0, subFailed = 0;

        for (Submission submission : submissions) {
            String newPath = submission.getFilePath();
            if (newPath == null) { subSkipped++; continue; }

            var block   = submission.getBlock();
            var exam    = block.getExam();
            var student = submission.getStudent();
            String oldPath = "submissions/exams/"
                    + exam.getExamId() + "/blocks/"
                    + block.getBlockId() + "/students/"
                    + student.getUserId() + "/"
                    + submission.getFileName();

            log.warn("[MINIO-FIX] Submission {}: bucket='{}' | oldPath='{}' | newPath='{}'",
                    submission.getSubmissionId(),
                    minioConfig.getBucket().getSubmissions(), oldPath, newPath);

            try {
                boolean oldExists = minioService.fileExists(minioConfig.getBucket().getSubmissions(), oldPath);
                boolean newExists = minioService.fileExists(minioConfig.getBucket().getSubmissions(), newPath);
                log.warn("[MINIO-FIX] Submission {}: oldExists={} newExists={}", submission.getSubmissionId(), oldExists, newExists);

                if (!oldExists) {
                    subSkipped++;
                    continue;
                }

                log.warn("Submission {}: will copy '{}' → '{}'", submission.getSubmissionId(), oldPath, newPath);
                if (!dryRun) {
                    minioService.copyObject(minioConfig.getBucket().getSubmissions(), oldPath, newPath);
                    minioService.deleteFile(minioConfig.getBucket().getSubmissions(), oldPath);
                }
                subMigrated++;
            } catch (Exception e) {
                String msg = "Submission " + submission.getSubmissionId() + ": " + e.getMessage();
                log.error("Fix FAILED: {}", msg, e);
                errors.add(msg);
                subFailed++;
            }
        }

        log.warn("=== MinIO Fix Objects DONE (dryRun={}) ===", dryRun);
        log.warn("ExamPapers  — migrated={} skipped={} failed={}", epMigrated, epSkipped, epFailed);
        log.warn("Submissions — migrated={} skipped={} failed={}", subMigrated, subSkipped, subFailed);

        return new MigrationReport(epMigrated, epSkipped, epFailed,
                subMigrated, subSkipped, subFailed, errors);
    }

    // ─── CASE 3: DB and MinIO have duplicate bucket prefixes ────────────────

    /**
     * Dùng khi DB và MinIO đều bị dư prefix "exam-papers/" hoặc "submissions/" (ví dụ db có "submissions/submissions/...").
     */
    public MigrationReport fixDuplicatePrefixes(boolean dryRun) {
        log.warn("=== MinIO Fix Duplicate Prefixes START (dryRun={}) ===", dryRun);
        List<String> errors = new ArrayList<>();

        List<ExamPaper> papers = examPaperRepository.findAll();
        int epMigrated = 0, epSkipped = 0, epFailed = 0;

        for (ExamPaper paper : papers) {
            String oldPath = paper.getFilePath();
            if (oldPath == null || !oldPath.startsWith("exam-papers/")) {
                epSkipped++; continue;
            }
            try {
                String newPath = oldPath.substring("exam-papers/".length());
                log.warn("ExamPaper {}: '{}' → '{}'", paper.getExamPaperId(), oldPath, newPath);
                if (!dryRun) migrateExamPaper(paper, oldPath, newPath);
                epMigrated++;
            } catch (Exception e) {
                String msg = "ExamPaper " + paper.getExamPaperId() + ": " + e.getMessage();
                log.error(msg, e); errors.add(msg); epFailed++;
            }
        }

        List<Submission> submissions = submissionRepository.findAll();
        int subMigrated = 0, subSkipped = 0, subFailed = 0;

        for (Submission submission : submissions) {
            String oldPath = submission.getFilePath();
            if (oldPath == null || !oldPath.startsWith("submissions/")) {
                subSkipped++; continue;
            }
            try {
                String newPath = oldPath.substring("submissions/".length());
                log.warn("Submission {}: '{}' → '{}'", submission.getSubmissionId(), oldPath, newPath);
                if (!dryRun) migrateSubmission(submission, oldPath, newPath);
                subMigrated++;
            } catch (Exception e) {
                String msg = "Submission " + submission.getSubmissionId() + ": " + e.getMessage();
                log.error(msg, e); errors.add(msg); subFailed++;
            }
        }

        log.warn("=== MinIO Fix Duplicate Prefixes DONE (dryRun={}) ===", dryRun);
        return new MigrationReport(epMigrated, epSkipped, epFailed,
                subMigrated, subSkipped, subFailed, errors);
    }

    // ─── CASE 2: DB còn path cũ, MinIO cũng path cũ (legacy) ────────────────

    /**
     * Dùng khi DB còn file_path theo format cũ.
     * Copy MinIO object sang path mới, update DB, xóa path cũ.
     */
    public MigrationReport migrateAll(boolean dryRun) {
        log.warn("=== MinIO Path Migration START (dryRun={}) ===", dryRun);
        List<String> errors = new ArrayList<>();

        List<ExamPaper> papers = examPaperRepository.findAll();
        int epMigrated = 0, epSkipped = 0, epFailed = 0;

        for (ExamPaper paper : papers) {
            String oldPath = paper.getFilePath();
            if (oldPath == null || !oldPath.startsWith(OLD_PREFIX)) {
                epSkipped++; continue;
            }
            try {
                String newPath = buildExamPaperPath(paper);
                log.warn("ExamPaper {}: '{}' → '{}'", paper.getExamPaperId(), oldPath, newPath);
                if (!dryRun) migrateExamPaper(paper, oldPath, newPath);
                epMigrated++;
            } catch (Exception e) {
                String msg = "ExamPaper " + paper.getExamPaperId() + ": " + e.getMessage();
                log.error(msg, e); errors.add(msg); epFailed++;
            }
        }

        List<Submission> submissions = submissionRepository.findAll();
        int subMigrated = 0, subSkipped = 0, subFailed = 0;

        for (Submission submission : submissions) {
            String oldPath = submission.getFilePath();
            if (oldPath == null || !oldPath.startsWith(OLD_PREFIX)) {
                subSkipped++; continue;
            }
            try {
                String newPath = buildSubmissionPath(submission);
                log.warn("Submission {}: '{}' → '{}'", submission.getSubmissionId(), oldPath, newPath);
                if (!dryRun) migrateSubmission(submission, oldPath, newPath);
                subMigrated++;
            } catch (Exception e) {
                String msg = "Submission " + submission.getSubmissionId() + ": " + e.getMessage();
                log.error(msg, e); errors.add(msg); subFailed++;
            }
        }

        log.warn("=== MinIO Path Migration DONE (dryRun={}) ===", dryRun);
        return new MigrationReport(epMigrated, epSkipped, epFailed,
                subMigrated, subSkipped, subFailed, errors);
    }

    // ─── SINGLE RECORD HELPERS ────────────────────────────────────────────────

    @Transactional
    protected void migrateExamPaper(ExamPaper paper, String oldPath, String newPath) {
        String bucket = minioConfig.getBucket().getExamPapers();
        minioService.copyObject(bucket, oldPath, newPath);
        paper.setFilePath(newPath);
        examPaperRepository.save(paper);
        try { minioService.deleteFile(bucket, oldPath); }
        catch (Exception e) { log.warn("Could not delete old object '{}': {}", oldPath, e.getMessage()); }
    }

    @Transactional
    protected void migrateSubmission(Submission submission, String oldPath, String newPath) {
        String bucket = minioConfig.getBucket().getSubmissions();
        minioService.copyObject(bucket, oldPath, newPath);
        submission.setFilePath(newPath);
        submissionRepository.save(submission);
        try { minioService.deleteFile(bucket, oldPath); }
        catch (Exception e) { log.warn("Could not delete old object '{}': {}", oldPath, e.getMessage()); }
    }

    // ─── PATH BUILDERS ────────────────────────────────────────────────────────

    private String buildExamPaperPath(ExamPaper paper) {
        var block = paper.getBlock();
        var exam  = block.getExam();
        return sanitize(expandSemester(exam.getSemester()) + "-" + exam.getAcademicYear()) + "/"
                + sanitize(block.getName()) + "/"
                + paper.getFileName();
    }

    private String buildSubmissionPath(Submission submission) {
        var block   = submission.getBlock();
        var exam    = block.getExam();
        var student = submission.getStudent();
        String studentDir = student.getFullName()
                + (student.getMssv() != null ? " - " + student.getMssv() : "");
        return sanitize(expandSemester(exam.getSemester()) + "-" + exam.getAcademicYear()) + "/"
                + sanitize(block.getName()) + "/"
                + sanitize(studentDir) + "/"
                + submission.getFileName();
    }

    // ─── UTILS ────────────────────────────────────────────────────────────────

    private String expandSemester(String code) {
        if (code == null) return "Unknown";
        return switch (code.toUpperCase()) {
            case "SP" -> "Spring";
            case "SU" -> "Summer";
            case "FA" -> "Fall";
            default   -> code;
        };
    }

    private String sanitize(String value) {
        if (value == null) return "unknown";
        return value.trim().replaceAll("[^a-zA-Z0-9\\-_. ]", "_");
    }

    // ─── BACKFILL: Populate jarFilePath / sourceCodePath for NULL Answers ─────

    /**
     * Re-parses each submission's zip from MinIO and populates the
     * jarFilePath + sourceCodePath for any Answer records that still have NULL paths.
     *
     * @param dryRun true = only log what would change, false = write to DB
     */
    @Transactional
    public BackfillReport backfillAnswerPaths(boolean dryRun) {
        log.warn("=== Backfill Answer Paths START (dryRun={}) ===", dryRun);
        List<String> errors = new ArrayList<>();
        int scanned = 0, updated = 0, skipped = 0, failed = 0;

        List<Submission> submissions = submissionRepository.findAll();

        for (Submission sub : submissions) {
            scanned++;
            List<Answer> answers = answerRepository
                    .findBySubmission_SubmissionIdOrderByQuestion_QuestionNumberAsc(sub.getSubmissionId());

            // Only process if ANY answer has null jarFilePath or sourceCodePath
            boolean hasNulls = answers.stream()
                    .anyMatch(a -> a.getJarFilePath() == null || a.getSourceCodePath() == null);
            if (!hasNulls) {
                skipped++;
                continue;
            }

            // Determine extension from fileName
            String fileName = sub.getFileName();
            String ext = (fileName != null && fileName.toLowerCase().endsWith(".rar")) ? ".rar" : ".zip";

            Path tmpFile = null;
            try {
                // Download zip from MinIO to temp file
                tmpFile = Files.createTempFile("backfill-", ext);
                try (InputStream in = minioService.downloadFile(
                        minioConfig.getBucket().getSubmissions(), sub.getFilePath())) {
                    Files.copy(in, tmpFile, StandardCopyOption.REPLACE_EXISTING);
                }

                // Re-parse
                ParsedSubmission parsed = submissionZipParser.parseFromTempFile(tmpFile, ext);

                // Map questionNumber → ParsedAnswer
                Map<Integer, ParsedSubmission.ParsedAnswer> parsedMap = parsed.answers().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                ParsedSubmission.ParsedAnswer::questionNumber, a -> a));

                // Update each answer that has nulls
                for (Answer answer : answers) {
                    if (answer.getJarFilePath() != null && answer.getSourceCodePath() != null) continue;

                    int qNum = answer.getQuestion().getQuestionNumber();
                    ParsedSubmission.ParsedAnswer pa = parsedMap.get(qNum);

                    String newJar = (pa != null) ? pa.jarEntryPath() : null;
                    String newSrc = (pa != null && pa.hasSource()) ? qNum + "/src/" : null;

                    log.warn("[BACKFILL] Submission {} Q{}: jar='{}' src='{}'",
                            sub.getSubmissionId(), qNum, newJar, newSrc);

                    if (!dryRun) {
                        answer.setJarFilePath(newJar);
                        answer.setSourceCodePath(newSrc);
                        answerRepository.save(answer);
                    }
                    updated++;
                }

            } catch (Exception e) {
                String msg = "Submission " + sub.getSubmissionId() + ": " + e.getMessage();
                log.warn("[BACKFILL] FAILED — {}", msg);
                errors.add(msg);
                failed++;
            } finally {
                if (tmpFile != null) {
                    try { Files.deleteIfExists(tmpFile); } catch (IOException ignored) {}
                }
            }
        }

        log.warn("=== Backfill Answer Paths END — scanned={} updated={} skipped={} failed={} ===",
                scanned, updated, skipped, failed);
        return new BackfillReport(scanned, updated, skipped, failed, errors);
    }
}
