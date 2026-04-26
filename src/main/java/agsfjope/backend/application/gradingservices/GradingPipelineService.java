package agsfjope.backend.application.gradingservices;

import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.enums.GradingMode;
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
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.domain.grading.FinalGradingScore;
import agsfjope.backend.domain.grading.QuestionScore;
import agsfjope.backend.domain.grading.ScoreCalculator;
import agsfjope.backend.domain.grading.ScoreCalculator.QuestionInput;
import agsfjope.backend.application.notificationservices.NotificationService;
import agsfjope.backend.application.ports.out.EmailService;
import agsfjope.backend.infrastructure.ai.AIReviewRequest;
import agsfjope.backend.infrastructure.ai.AIReviewResult;
import agsfjope.backend.infrastructure.ai.LLMReviewService;
import agsfjope.backend.infrastructure.grading.ArchiveExtractor;
import agsfjope.backend.infrastructure.grading.ExecutionResult;
import agsfjope.backend.infrastructure.grading.JavaParserAnalyzer;
import agsfjope.backend.infrastructure.grading.JarSandboxExecutor;
import agsfjope.backend.infrastructure.grading.ReflectionAnalyzer;
import agsfjope.backend.infrastructure.grading.StaticAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final SubmissionRepository       submissionRepository;
    private final UserRepository             userRepository;
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
    private final JavaParserAnalyzer  javaParserAnalyzer;  // [NEW] static AST analysis
    private final ReflectionAnalyzer  reflectionAnalyzer;  // [NEW] runtime class inspection
    private final ObjectMapper        objectMapper;
    private final NotificationService notificationService;
    private final EmailService        emailService;
    private final TransactionTemplate transactionTemplate;

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
    public void grade(Submission submission, User gradedByUser, Set<UUID> cancelledBlocks) {
        UUID subId = submission.getSubmissionId();
        log.info("Grading submission {} started", subId);

        // ── Short TX 1: Re-fetch, eager-init lazy associations, mark GRADING ──
        // All entity fields needed outside this TX must be initialized here
        // (Hibernate session closes after TX, proxies become detached).
        final UUID gradedByUserId = gradedByUser.getUserId();

        // Holder arrays to capture effectively-final references from lambda
        final Submission[] subHolder    = new Submission[1];
        final User[]       userHolder   = new User[1];
        final Block[]      blockHolder  = new Block[1];
        final ExamPaper[]  paperHolder  = new ExamPaper[1];
        final List<?>[]    answerHolder = new List[1];
        final GradingModeConfig[] modeHolder = new GradingModeConfig[1];

        transactionTemplate.executeWithoutResult(tx -> {
            Submission sub = submissionRepository.findById(subId)
                    .orElseThrow(() -> new IllegalStateException("Submission not found: " + subId));
            User user = userRepository.findById(gradedByUserId).orElse(gradedByUser);

            sub.setStatus(SubmissionStatus.GRADING);

            Block block = sub.getBlock();
            // Force-initialize Block proxy (accessing any field triggers initialization)
            block.getBlockId();
            // Force-init Block fields needed by sendGradingCompleteNotification()
            // (session closes after this TX — any uninitialized lazy proxy will throw outside)
            block.getName();
            if (block.getExam() != null) {
                block.getExam().getName(); // init Exam proxy
            }

            // Force-init Student (User) fields needed by sendGradingCompleteNotification()
            User student = sub.getStudent();
            if (student != null) {
                student.getUserId();
                student.getEmail();
                student.getFullName();
            }

            GradingModeConfig modeConfig = resolveGradingModeConfig(block);

            ExamPaper examPaper = examPaperRepository.findByBlock_BlockId(block.getBlockId())
                    .orElseThrow(() -> new IllegalStateException(
                            "No exam paper found for block " + block.getBlockId()));
            // Force-init ExamPaper fields
            examPaper.getFilePath();

            List<Answer> answers = answerRepository
                    .findBySubmission_SubmissionIdOrderByQuestion_QuestionNumberAsc(subId);
            // Force-initialize lazy Question associations for every answer
            for (Answer a : answers) {
                Question q = a.getQuestion();
                q.getQuestionNumber();
                q.getQuestionId();
                q.getTitle();
                q.getDescription();
                q.getMaxScore();
                q.getRemoveSpaces();
                q.getCaseSensitive();
            }

            subHolder[0]    = sub;
            userHolder[0]   = user;
            blockHolder[0]  = block;
            paperHolder[0]  = examPaper;
            answerHolder[0] = answers;
            modeHolder[0]   = modeConfig;
        }); // TX 1 commits here — all locks released

        final Submission finalSubmission = subHolder[0];
        final User finalGradedBy        = userHolder[0];
        final Block block               = blockHolder[0];
        final ExamPaper examPaper       = paperHolder[0];
        @SuppressWarnings("unchecked")
        final List<Answer> answers      = (List<Answer>) answerHolder[0];
        final GradingModeConfig modeConfig = modeHolder[0];

        if (answers.isEmpty()) {
            log.warn("Submission {} has no answers — marking GRADED with 0", subId);
            transactionTemplate.executeWithoutResult(tx ->
                saveGradingResult(finalSubmission, finalGradedBy, modeConfig,
                        new FinalGradingScore(List.of(), BigDecimal.ZERO,
                                BigDecimal.ZERO, BigDecimal.ZERO, false,
                                "No answers found in submission")));
            sendGradingCompleteNotification(finalSubmission, BigDecimal.ZERO, false);
            return;
        }

        // ── Short TX 2: Delete old results (commits immediately, releases locks) ──
        transactionTemplate.executeWithoutResult(tx -> {
            testCaseResultRepository.deleteByAnswer_Submission_SubmissionId(subId);
            aiReviewRepository.deleteByAnswer_Submission_SubmissionId(subId);
        }); // TX 2 commits — AIReviews table unlocked before async INSERTs begin

        String subExt  = getFileExtension(finalSubmission.getFilePath());
        String examExt = getFileExtension(examPaper.getFilePath());
        log.warn("[GRADING] Submission {} — filePath='{}' ext='{}' | exam filePath='{}' ext='{}'",
                subId, finalSubmission.getFilePath(), subExt, examPaper.getFilePath(), examExt);

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

                // ── Pre-check: extract JAR and src upfront ────────────────────
                // Both are needed; if either is missing, fail the question
                // immediately without running executor or AI.
                Path preJar    = null;
                Path preSrcDir = null;
                try {
                    preJar = archiveExtractor.extractStudentJar(
                            "submissions", finalSubmission.getFilePath(),
                            qNum, subExt, qWorkDir);
                } catch (Exception e) {
                    log.warn("[PRE-CHECK] Q{}: JAR extraction failed: {}", qNum, e.getMessage());
                }
                try {
                    preSrcDir = archiveExtractor.extractStudentSources(
                            "submissions", finalSubmission.getFilePath(),
                            qNum, subExt, qWorkDir);
                } catch (Exception e) {
                    log.warn("[PRE-CHECK] Q{}: src extraction failed: {}", qNum, e.getMessage());
                }

                // ── Missing-file handling ─────────────────────────────────────
                if (preJar == null || preSrcDir == null) {
                    String missingNote;
                    if (preJar == null && preSrcDir == null) {
                        missingNote = "Sinh viên không nộp file .jar và mã nguồn .java cho câu " + qNum;
                    } else if (preJar == null) {
                        missingNote = "Sinh viên không có file .jar cho câu " + qNum;
                    } else {
                        missingNote = "Sinh viên không có mã nguồn .java cho câu " + qNum;
                    }
                    log.warn("[MISSING-FILE] Q{}: {}", qNum, missingNote);

                    List<TestCase> tcList = testCaseRepository
                            .findByQuestion_QuestionIdOrderByTestCaseNumberAsc(question.getQuestionId());
                    List<TestCaseResult> errorResults = tcList.stream()
                            .map(tc -> buildErrorResult(answer, tc, missingNote))
                            .toList();
                    testCaseResultRepository.saveAll(errorResults);

                    final int totalTc      = tcList.size();
                    final BigDecimal maxSc = question.getMaxScore();
                    final String note      = missingNote;

                    // Force 0 ONLY in 2 cases requested:
                    // 1) Missing both .jar + /src
                    // 2) Missing /src (even if .jar exists)
                    boolean forceMissingRule =
                            (preJar == null && preSrcDir == null)
                            || (preJar != null && preSrcDir == null);

                    if (forceMissingRule) {
                        log.warn("[MISSING-FILE] Q{}: áp dụng rule thiếu file => ép 0 điểm", qNum);
                        // Use QuestionInput.missing() so ScoreCalculator applies mandatory FailIfMissingFile rule
                        questionInputs.put(qNum, QuestionInput.missing(maxSc, totalTc, note));
                        continue;
                    }

                    // Case còn lại: thiếu .jar nhưng vẫn có /src
                    // -> không chạy được test case (pass=0), nhưng vẫn AI review và KHÔNG ép 0 bởi missing rule.
                    final Path finalSrcDir = preSrcDir;
                    CompletableFuture<Void> aiTask = CompletableFuture.runAsync(() -> {
                        // [NEW] JAR null ở nhánh này — ReflectionAnalyzer sẽ skip, chỉ JavaParser chạy
                        StaticAnalysisResult staticAnalysis = runStaticAnalysis(finalSrcDir, null);

                        // [OLD] AIReviewResult aiResult = runAIReview(finalSubmission, answer, question, finalSrcDir);
                        AIReviewResult aiResult = runAIReview(
                                finalSubmission, answer, question, finalSrcDir, staticAnalysis);

                        transactionTemplate.executeWithoutResult(tx -> {
                            saveAIReview(answer, modeConfig.getMode().name(), aiResult);
                            // [OLD] questionInputs.put(qNum, QuestionInput.of(maxSc, 0, totalTc, aiResult));
                            questionInputs.put(qNum, QuestionInput.of(maxSc, 0, totalTc, aiResult, staticAnalysis));
                        });
                    }, aiExecutor);
                    aiTasks.add(aiTask);
                    continue;

                }

                // ── Step A: Run test cases (JAR + src both exist) ─────────────
                QuestionTcResult tcResult = runTestCases(
                        finalSubmission, examPaper, answer, question, qWorkDir, examExt, preJar);

                testCaseResultRepository.saveAll(tcResult.results());

                // ── Step B: AI Review ─────────────────────────────────────────
                final Path finalSrcDir      = preSrcDir;
                final Path finalJar         = preJar;   // [NEW] capture preJar — not effectively final because assigned in try-catch
                final QuestionTcResult finalTcResult = tcResult;

                // Run AI review (parallel, in background)
                CompletableFuture<Void> aiTask = CompletableFuture.runAsync(() -> {
                    StaticAnalysisResult staticAnalysis = runStaticAnalysis(finalSrcDir, finalJar);
                    AIReviewResult aiResult = runAIReview(
                            finalSubmission, answer, question, finalSrcDir, staticAnalysis);

                    transactionTemplate.executeWithoutResult(tx -> {
                        saveAIReview(answer, modeConfig.getMode().name(), aiResult);

                        log.warn("[AI-SCORE] Q{}: oopScore={} aiError={} oopViolated={}",
                                qNum, aiResult.oopScore(), aiResult.aiError(), aiResult.oopViolated());

                        questionInputs.put(qNum, finalTcResult.isTampered()
                                ? QuestionInput.tampered(question.getMaxScore(),
                                        finalTcResult.passCount(), finalTcResult.totalCount(),
                                        finalTcResult.tamperDetail())
                                : QuestionInput.of(question.getMaxScore(),
                                        finalTcResult.passCount(), finalTcResult.totalCount(),
                                        aiResult, staticAnalysis));
                    });
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
        FinalGradingScore finalScore;
        try {
            finalScore = scoreCalculator.calculate(modeConfig, questionInputs);
        } catch (Exception e) {
            log.error("Score calculation failed for submission {}: {}", subId, e.getMessage(), e);
            // Fallback to zero score so we still persist a result
            finalScore = new FinalGradingScore(List.of(), BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, false,
                    "Lỗi tính điểm: " + e.getMessage());
        }

        // ── Step D: Persist GradingResult ─────────────────────────────────────
        final FinalGradingScore finalScoreRef = finalScore;
        try {
            transactionTemplate.executeWithoutResult(tx ->
                saveGradingResult(finalSubmission, finalGradedBy, modeConfig, finalScoreRef));
            log.info("Grading submission {} complete. Score={}, Passed={}",
                    subId, finalScore.totalScore(), finalScore.passed());
        } catch (Exception e) {
            log.error("Failed to save grading result for submission {}: {}", subId, e.getMessage(), e);
            throw e;
        }

        // ── Step E: Persist per-question scores to Answer rows ─────────────────
        try {
            transactionTemplate.executeWithoutResult(tx ->
                saveAnswerScores(answers, finalScoreRef));
        } catch (Exception e) {
            log.warn("Failed to persist answer scores for submission {} (non-fatal): {}", subId, e.getMessage());
        }

        sendGradingCompleteNotification(finalSubmission, finalScore.totalScore(), finalScore.passed());
        log.info("Grading submission {} done.", subId);
    }

    // ─── TEST CASE EXECUTION ─────────────────────────────────────────────────

    private QuestionTcResult runTestCases(Submission submission, ExamPaper examPaper,
                                          Answer answer, Question question,
                                          Path workDir, String examExt, Path studentJar) {
        List<TestCase> testCases = testCaseRepository
                .findByQuestion_QuestionIdOrderByTestCaseNumberAsc(question.getQuestionId());

        List<TestCaseResult> results = new ArrayList<>();
        int passCount  = 0;
        boolean tampered = false;
        String tamperDetail = null;

        // Extract exam classes (exam-side only; student JAR already extracted by caller)
        Path examClassDir = null;
        Map<String, String> examChecksums = null;
        try {
            examClassDir = archiveExtractor.extractExamClasses(
                    "exam-papers", examPaper.getFilePath(),
                    question.getQuestionNumber(), examExt, workDir);
            // Compute checksums of exam .class files — used to detect tampered/modified files
            if (examClassDir != null) {
                examChecksums = jarSandboxExecutor.computeChecksums(examClassDir);
                log.debug("[TAMPER] Q{}: computed {} exam class checksums",
                        question.getQuestionNumber(), examChecksums.size());
            }
        } catch (Exception e) {
            log.error("Failed to extract exam classes for Q{}: {}",
                    question.getQuestionNumber(), e.getMessage());
        }

        final Map<String, String> finalChecksums = examChecksums;

        for (TestCase tc : testCases) {
            TestCaseResult result;

            // studentJar always non-null here (pre-check in grade() ensures this)
            String inputData = prepareInput(tc.getInputData(), question.getRemoveSpaces());
            log.warn("[TC] Q{} TC#{}: raw='{}' → prepared='{}'",
                    question.getQuestionNumber(), tc.getTestCaseNumber(),
                    tc.getInputData() != null
                        ? tc.getInputData().replace("\r", "\\r").replace("\n", "\\n")
                        : "NULL",
                    inputData.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t"));

            ExecutionResult execResult = jarSandboxExecutor.run(
                    studentJar, examClassDir != null ? examClassDir : studentJar.getParent(),
                    finalChecksums,  // pass real checksums — null only if exam extraction failed
                    inputData, tc.getTimeLimitMs());


            if (execResult.tamperedFiles()) {
                tampered = true;
                tamperDetail = execResult.errorMessage();
                result = buildTamperedResult(answer, tc, execResult.errorMessage());
            } else if (execResult.timeout()) {
                result = buildTimeoutResult(answer, tc, tc.getTimeLimitMs());
            } else if (execResult.exitCode() != 0) {
                result = buildErrorResult(answer, tc, execResult.errorMessage());
            } else {
                String extracted = extractOutputSection(execResult.stdout());
                boolean passed = compareOutput(
                        extracted, tc.getExpectedOutput(), question.getCaseSensitive());
                result = buildTcResult(answer, tc, execResult, extracted, passed);
                if (passed) passCount++;
            }

            log.warn("[TC-RESULT] Q{} TC#{}: status={} stdout='{}' err='{}'",
                    question.getQuestionNumber(), tc.getTestCaseNumber(),
                    result.getStatus(),
                    result.getActualOutput() != null
                        ? result.getActualOutput().replace("\n", "\\n").replace("\r", "").strip()
                        : "",
                    result.getErrorMessage() != null ? result.getErrorMessage().substring(
                        0, Math.min(120, result.getErrorMessage().length())) : "");

            results.add(result);
        }


        return new QuestionTcResult(results, passCount, testCases.size(), tampered, tamperDetail);
    }

    // ─── AI REVIEW ───────────────────────────────────────────────────────────

    /**
     * [NEW] Runs JavaParser (AST) + Reflection (runtime) analysis on the student submission.
     * Results are merged into a single {@link StaticAnalysisResult} that is used by:
     * <ol>
     *   <li>The AI prompt (via {@code structuredAnalysis} field in {@link AIReviewRequest}).</li>
     *   <li>{@link agsfjope.backend.domain.grading.ScoreCalculator} for immediate hardcode detection.</li>
     * </ol>
     * Failures in either analyzer are swallowed — never abort grading.
     *
     * @param srcDir     student’s source directory (may be null)
     * @param studentJar student’s compiled JAR (may be null when JAR is missing)
     */
    private StaticAnalysisResult runStaticAnalysis(Path srcDir, Path studentJar) {
        try {
            StaticAnalysisResult parserResult     = javaParserAnalyzer.analyze(srcDir);
            StaticAnalysisResult reflectionResult = reflectionAnalyzer.analyze(studentJar);
            StaticAnalysisResult merged           = StaticAnalysisResult.merge(parserResult, reflectionResult);
            log.debug("[STATIC-ANALYSIS] srcDir={} | classes={} interfaces={} hardcodes={}",
                    srcDir, merged.classCount(), merged.interfaceCount(),
                    merged.hardCodedSuspects().size());
            return merged;
        } catch (Exception e) {
            log.warn("[STATIC-ANALYSIS] Analysis failed (non-fatal): {}", e.getMessage());
            return StaticAnalysisResult.failure("Static analysis failed: " + e.getMessage());
        }
    }

    private AIReviewResult runAIReview(Submission submission, Answer answer,
                                       Question question, Path srcDir,
                                       StaticAnalysisResult staticAnalysis) {
        try {
            log.warn("[AI-SRC] sub={} Q{}: srcDir={}",
                    submission.getSubmissionId(), question.getQuestionNumber(),
                    srcDir != null ? srcDir.toAbsolutePath() : "NULL");

            String sourceCode = readSourceFiles(srcDir);
            log.warn("[AI-SRC] sourceCode length={}, blank={}",
                    sourceCode.length(), sourceCode.isBlank());

            // [NEW] Serialize structured analysis report for AI prompt
            String structuredAnalysisText = (staticAnalysis != null)
                    ? staticAnalysis.toFormattedReport()
                    : null;

            AIReviewRequest aiRequest = new AIReviewRequest(
                    question.getTitle(),
                    question.getDescription(),
                    sourceCode,
                    structuredAnalysisText,
                    "Vietnamese",
                    question.getMaxScore()
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    AIReview saveAIReview(Answer answer, String modelName, AIReviewResult ai) {
        try {
            // Store the full evaluation in RawResponse (JSONB) — this is the source of truth
            // for criteria breakdown since AIReviews table has no dedicated columns for them.
            Map<String, Object> rawMap = new java.util.LinkedHashMap<>();
            rawMap.put("oopScore",          ai.oopScore());
            rawMap.put("encapsulation",     ai.encapsulation());
            rawMap.put("inheritance",       ai.inheritance());
            rawMap.put("polymorphism",      ai.polymorphism());
            rawMap.put("designQuality",     ai.designQuality());
            rawMap.put("codeIntegrity",     ai.codeIntegrity());
            rawMap.put("violations",        ai.violations());
            rawMap.put("hardCodedValues",   ai.hardCodedValues());
            rawMap.put("criteriaResults",   ai.criteriaResults());
            rawMap.put("isOopViolated",     ai.oopViolated());
            rawMap.put("aiError",           ai.aiError());
            rawMap.put("errorMessage",      ai.errorMessage());
            String rawJson = objectMapper.writeValueAsString(rawMap);

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void saveGradingResult(Submission submission, User gradedBy,
                                   GradingModeConfig modeConfig, FinalGradingScore score) {
        // Delete old result using direct JPQL DELETE (bypasses Hibernate batch queue).
        // Entity-based delete (find + delete) causes batch ordering issues when
        // order_inserts=true: the INSERT runs before the DELETE → duplicate key violation.
        gradingResultRepository.deleteBySubmission_SubmissionId(submission.getSubmissionId());

        // Compute max score from question scores
        BigDecimal maxScore = score.questionScores().stream()
                .map(QuestionScore::maxScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Build notes from guard-triggered questions
        String note = buildNotes(score);

        log.warn("[SAVE-RESULT] sub={} totalScore={} tcScore={} oopScore={}",
                submission.getSubmissionId(), score.totalScore(),
                score.testCaseScore(), score.oopScore());

        GradingResult result = GradingResult.builder()
                .submission(submission)
                .gradingMode(modeConfig.getMode())
                .status(score.passed()
                        ? GradingResultStatus.PASS
                        : GradingResultStatus.FAIL)
                .totalScore(score.totalScore() != null ? score.totalScore() : BigDecimal.ZERO)
                .maxScore(maxScore)
                .testCaseScore(score.testCaseScore() != null ? score.testCaseScore() : BigDecimal.ZERO)
                .oopScore(score.oopScore() != null ? score.oopScore() : BigDecimal.ZERO)
                .gradedBy(gradedBy)
                .note(note)
                .build();

        gradingResultRepository.save(result);

        // Update submission status (explicit save — JPA dirty tracking may not fire in async context)
        submission.setStatus(SubmissionStatus.GRADED);
        submission.setGradedAt(OffsetDateTime.now());
        submissionRepository.save(submission);
        log.info("Submission {} status updated to GRADED", submission.getSubmissionId());
    }

    /**
     * Persists per-question scores (AnswerScore, GuardRuleTriggered, GuardRuleNote)
     * into the Answers table rows after the main GradingResult has been saved.
     *
     * <p>Matching is done by questionNumber — both the Answer entity (via its linked
     * Question) and the QuestionScore record carry a 1-based questionNumber.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void saveAnswerScores(List<Answer> answers, FinalGradingScore score) {
        // Build a lookup: questionNumber → QuestionScore
        Map<Integer, QuestionScore> scoreByQ = score.questionScores().stream()
                .collect(Collectors.toMap(QuestionScore::questionNumber, qs -> qs, (a, b) -> a));

        for (Answer answer : answers) {
            int qNum = answer.getQuestion().getQuestionNumber();
            QuestionScore qs = scoreByQ.get(qNum);
            if (qs == null) continue; // no score calculated for this question

            answer.setAnswerScore(qs.finalQuestionScore());
            answer.setGuardRuleTriggered(qs.guardRuleTriggered());
            answer.setGuardRuleNote(qs.note());
            answerRepository.save(answer);
        }
        log.debug("[ANSWER-SCORE] Saved per-question scores for {} answers", answers.size());
    }

    // ─── NOTIFICATION ─────────────────────────────────────────────────────────

    /**
     * Sends an in-app notification AND an email to the student after grading completes.
     * Failure is swallowed — a notification error must never abort the grading result.
     */
    private void sendGradingCompleteNotification(Submission submission,
                                                  BigDecimal totalScore, boolean passed) {
        try {
            UUID studentId   = submission.getStudent().getUserId();
            Block block      = submission.getBlock();
            String examName  = block.getExam()  != null ? block.getExam().getName()  : "kỳ thi";
            String blockName = block.getName() != null ? block.getName() : "block";

            String status = passed ? "✅ ĐẠT" : "❌ KHÔNG ĐẠT";
            String title  = "Kết quả chấm bài: " + examName;
            String body   = String.format(
                    "Bài nộp của bạn trong %s — %s đã được chấm xong.\n" +
                    "Điểm số: %.2f | Kết quả: %s\n" +
                    "Nhấn để xem chi tiết kết quả.",
                    examName, blockName,
                    totalScore, status);

            // In-app notification
            notificationService.createNotification(
                    studentId, title, body,
                    "GRADING_RESULT", submission.getSubmissionId());

            // Email notification (delegated to @Async SmtpEmailService)
            String studentEmail = submission.getStudent().getEmail();
            String studentName  = submission.getStudent().getFullName();
            if (studentEmail != null && !studentEmail.isBlank()) {
                emailService.sendGradingCompleteEmail(
                        studentEmail, studentName, examName, blockName,
                        totalScore.doubleValue(), passed);
            }

        } catch (Exception e) {
            log.warn("Failed to send grading notification for submission {}: {}",
                    submission.getSubmissionId(), e.getMessage());
        }
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

    /**
     * Extracts only the portion of stdout that appears after the last "OUTPUT:" marker.
     * Student programs often print UI prompts before the actual answer. The exam spec
     * requires students to print "OUTPUT:" before the result, so we extract only that part.
     * If no "OUTPUT:" marker is found, the full stdout is returned as-is.
     */
    private String extractOutputSection(String stdout) {
        if (stdout == null) return "";
        // Find last occurrence of OUTPUT: (case-insensitive) to handle multi-run outputs
        String upper = stdout.toUpperCase();
        int idx = upper.lastIndexOf("OUTPUT:");
        if (idx < 0) return stdout.stripTrailing();
        String afterMarker = stdout.substring(idx + "OUTPUT:".length());
        // Strip leading newline/spaces after the marker itself
        return afterMarker.stripLeading().stripTrailing();
    }

    private boolean compareOutput(String actual, String expected, boolean caseSensitive) {
        if (actual == null || expected == null) return false;
        String a = actual.stripTrailing();
        String e = expected.stripTrailing();
        return caseSensitive ? a.equals(e) : a.equalsIgnoreCase(e);
    }

    private String prepareInput(String input, boolean removeSpaces) {
        if (input == null) return "\n";
        // Convert CRLF to LF
        String normalized = input.replace("\r\n", "\n").replace("\r", "\n");
        if (!removeSpaces) {
            return normalized.endsWith("\n") ? normalized : normalized + "\n";
        }
        // REMOVE_SPACES mode: Tokenize everything and put one token per line.
        // This makes Scanner's nextInt/nextDouble completely foolproof regardless of
        // whether the original DB input used spaces or newlines as separators.
        return normalized.lines()
                         .map(String::strip)
                         .filter(line -> !line.isEmpty())
                         // Replace spaces inside the line with newlines so each token is isolated
                         .map(line -> line.replaceAll("\\s+", "\n"))
                         .collect(java.util.stream.Collectors.joining("\n")) + "\n";
    }


    // ─── RESULT BUILDERS ─────────────────────────────────────────────────────

    private TestCaseResult buildTcResult(Answer answer, TestCase tc,
                                         ExecutionResult exec, String extractedOutput, boolean passed) {
        return TestCaseResult.builder()
                .answer(answer)
                .testCase(tc)
                .status(passed ? TestCaseStatus.PASS_TESTCASE : TestCaseStatus.FAIL_TESTCASE)
                .actualOutput(extractedOutput)   // store only the extracted answer, not the full stdout
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
        // 1. Read grading mode directly from Exam.GradingMode
        // Business requirement: grading mode is per exam, not global system default.
        GradingMode examMode = (block != null && block.getExam() != null)
                ? block.getExam().getGradingMode()
                : null;

        if (examMode == null) {
            log.warn("Exam grading mode is null for block {} — falling back to MODE_1",
                    block != null ? block.getBlockId() : null);
        }
        final GradingMode activeMode = examMode != null ? examMode : GradingMode.MODE_1;

        // 2. Look up GradingModeConfig by exam mode
        return gradingModeConfigRepository.findByMode(activeMode)
            .orElseGet(() -> {
                // If specific mode config is missing, try MODE_1 from DB first.
                // This avoids relying on hard-coded defaults when admin data is partially seeded.
                return gradingModeConfigRepository.findByMode(GradingMode.MODE_1)
                    .orElseGet(() -> {
                    log.warn("No GradingModeConfig found for mode {} and MODE_1 — using built-in MODE_1 default", activeMode);
                    return GradingModeConfig.builder()
                        .mode(GradingMode.MODE_1)
                        .displayName("Default (100% Test Cases)")
                        .testCaseWeight(new BigDecimal("100"))
                        .oopWeight(BigDecimal.ZERO)
                        .oopCommentOnly(false)
                        .failIfZeroTestCase(false)
                        .failIfOopViolated(false)
                        .isActive(true)
                        .build();
                    });
            });
    }

    private String getFileExtension(String filePath) {
        if (filePath == null) return ".zip";
        int idx = filePath.lastIndexOf('.');
        return idx >= 0 ? filePath.substring(idx).toLowerCase() : ".zip";
    }



    private record QuestionTcResult(
            List<TestCaseResult> results,
            int passCount,
            int totalCount,
            boolean isTampered,
            String tamperDetail
    ) {}
}
