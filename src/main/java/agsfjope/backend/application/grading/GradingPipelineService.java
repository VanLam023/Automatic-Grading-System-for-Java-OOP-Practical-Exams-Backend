package agsfjope.backend.application.grading;

import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.enums.GradingResultStatus;
import agsfjope.backend.core.enums.SubmissionStatus;
import agsfjope.backend.core.enums.TestCaseStatus;
import agsfjope.backend.core.repositories.config.GradingModeConfigRepository;
import agsfjope.backend.core.repositories.exampaper.ExamPaperRepository;
import agsfjope.backend.core.repositories.exampaper.TestCaseRepository;
import agsfjope.backend.core.repositories.grading.AIReviewRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.grading.TestCaseResultRepository;
import agsfjope.backend.core.repositories.submission.AnswerRepository;
import agsfjope.backend.domain.grading.FinalGradingScore;
import agsfjope.backend.domain.grading.QuestionScore;
import agsfjope.backend.domain.grading.ScoreCalculator;
import agsfjope.backend.domain.grading.ScoreCalculator.QuestionInput;
import agsfjope.backend.infrastructure.ai.AIReviewRequest;
import agsfjope.backend.infrastructure.ai.AIReviewResult;
import agsfjope.backend.infrastructure.ai.LLMReviewService;
import agsfjope.backend.infrastructure.grading.ArchiveExtractor;
import agsfjope.backend.infrastructure.grading.ExecutionResult;
import agsfjope.backend.infrastructure.grading.JarSandboxExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;

/**
 * Core pipeline that grades a single {@link Submission}.
 *
 * <h3>Pipeline Steps per Submission:</h3>
 * <ol>
 *   <li>Create a temp working directory.</li>
 *   <li>For each {@link Answer} (question), in parallel:
 *     <ul>
 *       <li>Extract student JAR + exam .class files from MinIO archive.</li>
 *       <li>Run each {@link TestCase} through {@link JarSandboxExecutor}.</li>
 *       <li>Compare output; save {@link TestCaseResult} entities.</li>
 *       <li>Read student .java source files for AI review.</li>
 *       <li>Call {@link LLMReviewService} → save {@link AIReview} entity.</li>
 *     </ul>
 *   </li>
 *   <li>Invoke {@link ScoreCalculator} with all per-question inputs.</li>
 *   <li>Save {@link GradingResult} with final scores and PASS/FAIL status.</li>
 *   <li>Update {@link Submission#status} to GRADED.</li>
 *   <li>Cleanup temp directories.</li>
 * </ol>
 *
 * <p>Any exception during step 2 for a single answer is caught and logged —
 * the question gets 0 points but does NOT abort other questions or the submission.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradingPipelineService {

    // Parallel AI calls per submission (up to 5 questions → 5 parallel AI calls)
    private static final int AI_PARALLELISM = 5;

    private final AnswerRepository           answerRepository;
    private final TestCaseRepository         testCaseRepository;
    private final TestCaseResultRepository   testCaseResultRepository;
    private final AIReviewRepository         aiReviewRepository;
    private final GradingResultRepository    gradingResultRepository;
    private final ExamPaperRepository        examPaperRepository;
    private final GradingModeConfigRepository gradingModeConfigRepository;

    private final ArchiveExtractor    archiveExtractor;
    private final JarSandboxExecutor  jarSandboxExecutor;
    private final LLMReviewService    llmReviewService;
    private final ScoreCalculator     scoreCalculator;
    private final ObjectMapper        objectMapper;

    // ─── PUBLIC API ──────────────────────────────────────────────────────────

    /**
     * Grades a single submission end-to-end.
     * Called by {@link GradingService} from an async thread pool.
     *
     * @param submission      the submission to grade
     * @param gradedByUser    the staff user who triggered grading (for audit)
     * @param cancelledBlocks shared set of block IDs that have been requested to stop;
     *                        checked before each answer — if set, grading stops cooperatively
     * @throws GradingCancelledException if the block was cancelled mid-submission
     */
    @Transactional
    public void grade(Submission submission, User gradedByUser, Set<UUID> cancelledBlocks) {
        UUID subId = submission.getSubmissionId();
        log.info("Grading submission {} started", subId);

        // Mark as GRADING immediately
        submission.setStatus(SubmissionStatus.GRADING);

        // Resolve grading mode config  (active config for the block's exam)
        Block block = submission.getBlock();
        GradingModeConfig modeConfig = resolveGradingModeConfig(block);

        // Find exam paper for the block
        ExamPaper examPaper = examPaperRepository.findByBlock_BlockId(block.getBlockId())
                .orElseThrow(() -> new IllegalStateException(
                        "No exam paper found for block " + block.getBlockId()));

        // Load all answers for this submission
        List<Answer> answers = answerRepository
                .findBySubmission_SubmissionIdOrderByQuestion_QuestionNumberAsc(subId);

        if (answers.isEmpty()) {
            log.warn("Submission {} has no answers — marking GRADED with 0", subId);
            saveGradingResult(submission, gradedByUser, modeConfig,
                    new FinalGradingScore(List.of(), BigDecimal.ZERO,
                            BigDecimal.ZERO, BigDecimal.ZERO, false,
                            "No answers found in submission"));
            return;
        }

        // ── Per-question grading (AI in parallel, TC sequential per question) ──
        String subExt = getFileExtension(submission.getFilePath());
        String examExt = getFileExtension(examPaper.getFilePath());

        // BUG FIX: ConcurrentHashMap because multiple CompletableFuture tasks write to this map
        Map<Integer, QuestionInput> questionInputs = new java.util.concurrent.ConcurrentHashMap<>();
        List<CompletableFuture<Void>> aiTasks = new ArrayList<>();
        ExecutorService aiExecutor = Executors.newFixedThreadPool(
                Math.min(AI_PARALLELISM, answers.size()));

        Path subWorkDir = null;
        try {
            subWorkDir = archiveExtractor.createWorkDir("sub_" + subId.toString().replace("-", ""));

            for (Answer answer : answers) {
                // ── CANCELLATION CHECK (before each question) ────────────────
                if (cancelledBlocks != null && cancelledBlocks.contains(block.getBlockId())) {
                    log.info("Grading of submission {} cancelled before Q{}",
                            subId, answer.getQuestion().getQuestionNumber());
                    submission.setStatus(SubmissionStatus.SUBMITTED);
                    aiExecutor.shutdownNow();
                    throw new GradingCancelledException(
                            "Grading stopped by staff for block " + block.getBlockId());
                }

                Question question   = answer.getQuestion();
                int qNum            = question.getQuestionNumber();
                Path qWorkDir       = archiveExtractor.createWorkDir(
                        "sub_" + subId.toString().replace("-", "") + "_q" + qNum);

                // ── Step A: Run test cases ──────────────────────────────────
                QuestionTcResult tcResult = runTestCases(
                        submission, examPaper, answer, question, qWorkDir, subExt, examExt);

                // Save TestCaseResult entities
                testCaseResultRepository.saveAll(tcResult.results());

                // ── Step B: AI Review (parallel, in background) ─────────────
                final Path finalQWorkDir = qWorkDir;
                final String finalSubExt = subExt;
                CompletableFuture<Void> aiTask = CompletableFuture.runAsync(() -> {
                    AIReviewResult aiResult = runAIReview(
                            submission, examPaper, answer, question, finalQWorkDir, finalSubExt);
                    saveAIReview(answer, modeConfig.getMode().name(), aiResult);

                    // Build QuestionInput
                    questionInputs.put(qNum, tcResult.isTampered()
                            ? QuestionInput.tampered(question.getMaxScore(),
                                    tcResult.passCount(), tcResult.totalCount(),
                                    tcResult.tamperDetail())
                            : QuestionInput.of(question.getMaxScore(),
                                    tcResult.passCount(), tcResult.totalCount(),
                                    aiResult));
                }, aiExecutor);

                aiTasks.add(aiTask);
            }

            // Wait for all AI tasks to complete
            CompletableFuture.allOf(aiTasks.toArray(CompletableFuture[]::new)).join();

        } catch (GradingCancelledException e) {
            // Re-throw before generic catch — signals GradingService to break the loop.
            // shutdownNow() to interrupt any running AI threads immediately.
            // Cleanup is handled by the finally block (avoid calling it twice).
            aiExecutor.shutdownNow();
            throw e;
        } catch (Exception e) {
            log.error("Grading pipeline error for submission {}: {}", subId, e.getMessage(), e);
        } finally {
            // Always shutdown executor and clean up temp dirs
            aiExecutor.shutdown();
            if (subWorkDir != null) archiveExtractor.cleanupWorkDir(subWorkDir);
        }

        // ── Step C: Score calculation ─────────────────────────────────────────
        FinalGradingScore finalScore = scoreCalculator.calculate(modeConfig, questionInputs);

        // ── Step D: Persist ───────────────────────────────────────────────────
        saveGradingResult(submission, gradedByUser, modeConfig, finalScore);
        log.info("Grading submission {} complete. Score={}, Passed={}",
                subId, finalScore.totalScore(), finalScore.passed());
    }

    // ─── TEST CASE EXECUTION ─────────────────────────────────────────────────

    private QuestionTcResult runTestCases(Submission submission, ExamPaper examPaper,
                                          Answer answer, Question question,
                                          Path workDir, String subExt, String examExt) {
        List<TestCase> testCases = testCaseRepository
                .findByQuestion_QuestionIdOrderByTestCaseNumberAsc(question.getQuestionId());

        List<TestCaseResult> results = new ArrayList<>();
        int passCount  = 0;
        boolean tampered = false;
        String tamperDetail = null;

        // Extract student JAR once per question
        Path studentJar = null;
        Path examClassDir = null;

        try {
            studentJar = archiveExtractor.extractStudentJar(
                    "submissions", submission.getFilePath(),
                    question.getQuestionNumber(), subExt, workDir);

            examClassDir = archiveExtractor.extractExamClasses(
                    "exam-papers", examPaper.getFilePath(),
                    question.getQuestionNumber(), examExt, workDir);
        } catch (Exception e) {
            log.error("Failed to extract archives for submission {} Q{}: {}",
                    submission.getSubmissionId(), question.getQuestionNumber(), e.getMessage());
        }

        for (TestCase tc : testCases) {
            TestCaseResult result;

            if (studentJar == null) {
                result = buildErrorResult(answer, tc, "Failed to extract student JAR");
            } else {
                String inputData = prepareInput(tc.getInputData(), question.getRemoveSpaces());

                ExecutionResult execResult = jarSandboxExecutor.run(
                        studentJar, examClassDir != null ? examClassDir : studentJar.getParent(),
                        null,   // checksums: optional, handled in executor
                        inputData, tc.getTimeLimitMs());

                // Handle tampered file detection
                if (execResult.tamperedFiles()) {
                    tampered = true;
                    tamperDetail = execResult.errorMessage();
                    result = buildTamperedResult(answer, tc, execResult.errorMessage());
                } else if (execResult.timeout()) {
                    result = buildTimeoutResult(answer, tc, tc.getTimeLimitMs());
                } else if (execResult.exitCode() != 0) {
                    result = buildErrorResult(answer, tc, execResult.errorMessage());
                } else {
                    boolean passed = compareOutput(
                            execResult.stdout(), tc.getExpectedOutput(), question.getCaseSensitive());
                    result = buildTcResult(answer, tc, execResult, passed);
                    if (passed) passCount++;
                }
            }

            results.add(result);
        }

        return new QuestionTcResult(results, passCount, testCases.size(), tampered, tamperDetail);
    }

    // ─── AI REVIEW ───────────────────────────────────────────────────────────

    private AIReviewResult runAIReview(Submission submission, ExamPaper examPaper,
                                       Answer answer, Question question,
                                       Path workDir, String subExt) {
        try {
            Path srcDir = archiveExtractor.extractStudentSources(
                    "submissions", submission.getFilePath(),
                    question.getQuestionNumber(), subExt, workDir);

            String sourceCode = readSourceFiles(srcDir);

            AIReviewRequest aiRequest = new AIReviewRequest(
                    question.getTitle(),
                    question.getDescription(),
                    sourceCode,
                    "Vietnamese"  // language resolved from SystemConfig inside LLMReviewService
            );

            return llmReviewService.review(aiRequest);
        } catch (Exception e) {
            log.error("AI review failed for submission {} Q{}: {}",
                    submission.getSubmissionId(), question.getQuestionNumber(), e.getMessage());
            return AIReviewResult.failure("Source extraction failed: " + e.getMessage());
        }
    }

    private String readSourceFiles(Path srcDir) {
        if (srcDir == null) return "(no source files found)";
        try (var walk = java.nio.file.Files.walk(srcDir)) {
            StringBuilder sb = new StringBuilder();
            walk.filter(p -> p.toString().endsWith(".java"))
                .sorted()
                .forEach(p -> {
                    try {
                        sb.append("// ─── ").append(p.getFileName()).append(" ───\n");
                        sb.append(java.nio.file.Files.readString(p)).append("\n\n");
                    } catch (Exception e) {
                        sb.append("// [Could not read ").append(p.getFileName()).append("]\n\n");
                    }
                });
            return sb.toString().isBlank() ? "(no .java files found)" : sb.toString();
        } catch (Exception e) {
            return "(failed to read source dir: " + e.getMessage() + ")";
        }
    }

    // ─── SAVE HELPERS ────────────────────────────────────────────────────────

    private AIReview saveAIReview(Answer answer, String modelName, AIReviewResult ai) {
        try {
            String rawJson = objectMapper.writeValueAsString(Map.of(
                    "oopScore",         ai.oopScore() != null ? ai.oopScore() : "null",
                    "violations",       ai.violations(),
                    "hardCodedValues",  ai.hardCodedValues(),
                    "isOopViolated",    ai.oopViolated(),
                    "aiError",          ai.aiError()
            ));

            AIReview review = AIReview.builder()
                    .answer(answer)
                    .aiModel(modelName)
                    .oopScore(ai.oopScore())
                    .comment(ai.comment())
                    .rawResponse(rawJson)
                    .isOopViolated(ai.oopViolated())
                    .build();

            return aiReviewRepository.save(review);
        } catch (Exception e) {
            log.error("Failed to save AIReview: {}", e.getMessage());
            return null;
        }
    }

    private void saveGradingResult(Submission submission, User gradedBy,
                                   GradingModeConfig modeConfig, FinalGradingScore score) {
        // Delete old result if re-grading
        gradingResultRepository.findBySubmission_SubmissionId(submission.getSubmissionId())
                .ifPresent(gradingResultRepository::delete);

        // Compute max score from question scores
        BigDecimal maxScore = score.questionScores().stream()
                .map(QuestionScore::maxScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Build notes from guard-triggered questions
        String note = buildNotes(score);

        GradingResult result = GradingResult.builder()
                .submission(submission)
                .gradingMode(modeConfig.getMode())
                .status(score.passed()
                        ? GradingResultStatus.PASS
                        : GradingResultStatus.FAIL)
                .totalScore(score.totalScore())
                .maxScore(maxScore)
                .testCaseScore(score.testCaseScore())
                .oopScore(score.oopScore())
                .gradedBy(gradedBy)
                .note(note)
                .build();

        gradingResultRepository.save(result);

        // Update submission status
        submission.setStatus(SubmissionStatus.GRADED);
        submission.setGradedAt(OffsetDateTime.now());
    }

    private String buildNotes(FinalGradingScore score) {
        StringBuilder sb = new StringBuilder();
        if (score.globalNote() != null) {
            sb.append(score.globalNote()).append("\n");
        }
        score.questionScores().stream()
                .filter(QuestionScore::guardRuleTriggered)
                .forEach(qs -> sb.append("Câu ").append(qs.questionNumber())
                        .append(": ").append(qs.note()).append("\n"));
        return sb.toString().isBlank() ? null : sb.toString().trim();
    }

    // ─── OUTPUT COMPARISON ───────────────────────────────────────────────────

    private boolean compareOutput(String actual, String expected, boolean caseSensitive) {
        if (actual == null || expected == null) return false;
        String a = actual.stripTrailing();
        String e = expected.stripTrailing();
        return caseSensitive ? a.equals(e) : a.equalsIgnoreCase(e);
    }

    private String prepareInput(String input, boolean removeSpaces) {
        if (input == null) return "";
        return removeSpaces ? input.replaceAll("\\s+", "") : input;
    }

    // ─── RESULT BUILDERS ─────────────────────────────────────────────────────

    private TestCaseResult buildTcResult(Answer answer, TestCase tc,
                                         ExecutionResult exec, boolean passed) {
        return TestCaseResult.builder()
                .answer(answer)
                .testCase(tc)
                .status(passed ? TestCaseStatus.PASS_TESTCASE : TestCaseStatus.FAIL_TESTCASE)
                .actualOutput(exec.stdout())
                .executionTimeMs((int) exec.executionTimeMs())
                .scoreEarned(passed ? tc.getScore() : BigDecimal.ZERO)
                .build();
    }

    private TestCaseResult buildErrorResult(Answer answer, TestCase tc, String msg) {
        return TestCaseResult.builder()
                .answer(answer).testCase(tc)
                .status(TestCaseStatus.ERROR)
                .errorMessage(msg)
                .scoreEarned(BigDecimal.ZERO)
                .build();
    }

    private TestCaseResult buildTimeoutResult(Answer answer, TestCase tc, long limitMs) {
        return TestCaseResult.builder()
                .answer(answer).testCase(tc)
                .status(TestCaseStatus.TIMEOUT)
                .errorMessage("Execution exceeded time limit (" + limitMs + "ms)")
                .executionTimeMs((int) limitMs)
                .scoreEarned(BigDecimal.ZERO)
                .build();
    }

    private TestCaseResult buildTamperedResult(Answer answer, TestCase tc, String detail) {
        return TestCaseResult.builder()
                .answer(answer).testCase(tc)
                .status(TestCaseStatus.ERROR)
                .errorMessage("Exam file tampering detected: " + detail)
                .scoreEarned(BigDecimal.ZERO)
                .build();
    }

    // ─── UTILITY ─────────────────────────────────────────────────────────────

    private GradingModeConfig resolveGradingModeConfig(Block block) {
        // Use the first active grading mode config (system-wide mode set by admin)
        return gradingModeConfigRepository.findAllByOrderByModeAsc().stream()
                .filter(GradingModeConfig::getIsActive)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active GradingModeConfig found"));
    }

    private String getFileExtension(String filePath) {
        if (filePath == null) return ".zip";
        int idx = filePath.lastIndexOf('.');
        return idx >= 0 ? filePath.substring(idx).toLowerCase() : ".zip";
    }

    // ─── INNER RECORD ────────────────────────────────────────────────────────

    private record QuestionTcResult(
            List<TestCaseResult> results,
            int passCount,
            int totalCount,
            boolean isTampered,
            String tamperDetail
    ) {}
}
