package agsfjope.backend.domain.grading;

import agsfjope.backend.core.entities.GradingModeConfig;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.infrastructure.ai.AIReviewResult;
import agsfjope.backend.infrastructure.grading.StaticAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calculates the final score for a submission based on test case results and AI OOP review.
 *
 * <h3>Scoring Algorithm (3-step, per-question):</h3>
 *
 * <pre>
 * Step 1 — Raw Per-Question Score:
 *   tcRaw  = (passCount / totalTcCount) × maxScore
 *   oopRaw = aiOopScore    (AI now returns score directly on question maxScore scale)
 *
 * Step 2 — Apply Guard Rules (from GradingModeConfig):
 *   ┌─ FailIfOopViolated && isOopViolated  → questionFinal = 0, record note with original scores
 *   ├─ FailIfZeroTestCase && passCount==0  → questionFinal = 0, record note
 *   └─ OopCommentOnly                      → oopRaw not added to score (still recorded)
 *
 * Step 3 — Weighted Total:
 *   tcTotal   = Σ tcRaw  (each question)
 *   oopTotal  = Σ effectiveOopRaw (each question, 0 if OopCommentOnly)
 *   finalScore = tcTotal × TestCaseWeight + oopTotal × OopWeight
 *   passed     = finalScore > GRADING_PASS_THRESHOLD  (configurable via SystemConfigs, default = 0)
 * </pre>
 *
 * <p>Guard rules force the QUESTION score to 0 but the global weight-based calculation
 * still uses the (effectively 0) contribution from that question.</p>
 *
 * <p>The pass threshold is read dynamically from {@code SystemConfigs} table
 * (key: {@code GRADING_PASS_THRESHOLD}) on each grading call, so Admin changes
 * take effect immediately without restarting the server.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreCalculator {

    /** Config key for the pass threshold stored in SystemConfigs table. */
    private static final String   PASS_THRESHOLD_KEY = "GRADING_PASS_THRESHOLD";

    /** Fallback threshold when the config key is missing from DB (any score > 0 passes). */
    private static final BigDecimal DEFAULT_PASS_THRESHOLD = BigDecimal.ZERO;

    private static final int          SCALE    = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * Repository for reading system-level key-value configuration.
     * Injected via constructor (Clean Architecture: core layer depends only on core interfaces).
     */
    private final SystemConfigRepository systemConfigRepository;

    /**
     * Calculates the full grading result for a submission.
     *
     * <p>The pass threshold is loaded dynamically from {@code SystemConfigs}
     * (key: {@code GRADING_PASS_THRESHOLD}) on each call, so Admin changes
     * take effect immediately without server restart.</p>
     *
     * @param config          the grading mode config (weights + guard rules)
     * @param questionInputs  per-question data keyed by question number
     * @return FinalGradingScore with per-question breakdown and final totals
     */
    public FinalGradingScore calculate(GradingModeConfig config,
                                       Map<Integer, QuestionInput> questionInputs) {

        // Load pass threshold from DB; fall back to DEFAULT_PASS_THRESHOLD (0) if key missing.
        // This allows Admin to change the threshold in SystemConfigs without restarting the server.
        BigDecimal passThreshold = loadPassThreshold();

        List<QuestionScore> questionScores = new ArrayList<>();
        BigDecimal sumTcRaw  = BigDecimal.ZERO;
        BigDecimal sumOopRaw = BigDecimal.ZERO;

        for (Map.Entry<Integer, QuestionInput> entry : questionInputs.entrySet()) {
            int questionNumber = entry.getKey();
            QuestionInput input = entry.getValue();

            QuestionScore qScore = scoreQuestion(questionNumber, input, config);
            questionScores.add(qScore);

            // Step 3: accumulate — guard-forced questions contribute 0 to BOTH sums
            sumTcRaw  = sumTcRaw.add(
                    qScore.guardRuleTriggered() ? BigDecimal.ZERO : qScore.rawTcScore()
            );
            sumOopRaw = sumOopRaw.add(
                    config.getOopCommentOnly()  ? BigDecimal.ZERO :
                    qScore.guardRuleTriggered()  ? BigDecimal.ZERO : qScore.rawOopScore()
            );
        }

        // Apply weights to get the two weighted totals
        BigDecimal tcWeight  = config.getTestCaseWeight().divide(new BigDecimal("100"), 4, ROUNDING);
        BigDecimal oopWeight = config.getOopWeight().divide(new BigDecimal("100"), 4, ROUNDING);

        BigDecimal tcTotal    = sumTcRaw.multiply(tcWeight).setScale(SCALE, ROUNDING);
        BigDecimal oopTotal   = sumOopRaw.multiply(oopWeight).setScale(SCALE, ROUNDING);
        BigDecimal finalScore = tcTotal.add(oopTotal).setScale(SCALE, ROUNDING);

        // Compare against threshold read from DB: finalScore > passThreshold → PASS
        boolean passed = finalScore.compareTo(passThreshold) > 0;

        String globalNote = null;
        if (config.getOopCommentOnly()) {
            globalNote = "Chế độ: OOP chỉ nhận xét, không tính điểm.";
        }

        log.debug("Score calculated: tc={}, oop={}, final={}, passThreshold={}, passed={}",
                tcTotal, oopTotal, finalScore, passThreshold, passed);

        return new FinalGradingScore(questionScores, finalScore, tcTotal, oopTotal, passed, globalNote);
    }

    /**
     * Reads the pass threshold from {@code SystemConfigs}.
     *
     * <p>If the key {@code GRADING_PASS_THRESHOLD} is missing or cannot be parsed,
     * falls back to {@code DEFAULT_PASS_THRESHOLD} (0) and logs a warning.
     * This ensures the grading pipeline never fails due to a missing config.
     *
     * @return the configured pass threshold, or 0 as fallback
     */
    private BigDecimal loadPassThreshold() {
        try {
            return systemConfigRepository
                    .findByConfigKey(PASS_THRESHOLD_KEY)
                    .map(config -> new BigDecimal(config.getConfigValue()))
                    .orElseGet(() -> {
                        log.warn("[GRADING] Config key '{}' not found in SystemConfigs — "
                                + "falling back to default threshold: {}",
                                PASS_THRESHOLD_KEY, DEFAULT_PASS_THRESHOLD);
                        return DEFAULT_PASS_THRESHOLD;
                    });
        } catch (NumberFormatException e) {
            // Config value is not a valid number — log and use safe default
            log.warn("[GRADING] Config key '{}' has invalid numeric value — "
                    + "falling back to default threshold: {}. Error: {}",
                    PASS_THRESHOLD_KEY, DEFAULT_PASS_THRESHOLD, e.getMessage());
            return DEFAULT_PASS_THRESHOLD;
        }
    }

    // ─── PER-QUESTION SCORING ─────────────────────────────────────────────────

    private QuestionScore scoreQuestion(int questionNumber, QuestionInput input,
                                        GradingModeConfig config) {
        BigDecimal maxScore = input.maxScore();
        int total   = input.totalTcCount();
        int passed  = input.passTcCount();

        // Step 1: Raw scores
        BigDecimal tcRaw  = calculateTcRaw(passed, total, maxScore);
        BigDecimal oopRaw = calculateOopRaw(input.aiResult(), maxScore);

        // Step 2: Guard rules (in priority order — first match wins)

        // 2a. FailIfMissingFile (MANDATORY) — 0 if student did not submit .jar or .java src
        if (input.missingFile()) {
            String note = "Điểm gốc: TC=0, OOP=0 → 0đ do " +
                    (input.missingDetail() != null ? input.missingDetail()
                            : "thiếu file nộp (không có .jar hoặc mã nguồn .java)");
            return new QuestionScore(questionNumber, maxScore,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, true, note, 0, total);
        }

        // 2b. Tampered files — forced 0 regardless of any config
        if (input.hasExamFileTampering()) {
            String note = buildTamperNote(tcRaw, oopRaw, input.tamperDetail());
            return new QuestionScore(questionNumber, maxScore,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, true, note, passed, total);
        }

        // 2c. FailIfHardCoded (MANDATORY) — 0 if STATIC ANALYSIS detects hard-coded return values
        // [OLD] This used to check AI result; now uses JavaParser result (no need to wait for AI):
        // if (input.aiResult() != null
        //         && !input.aiResult().aiError()
        //         && input.aiResult().hardCodedValues() != null
        //         && !input.aiResult().hardCodedValues().isEmpty()) {
        //     String note = buildHardCodeNote(tcRaw, oopRaw, input.aiResult().hardCodedValues());
        // }
        // [NEW] Use JavaParser static analysis result — immediate, no AI dependency
        if (input.staticAnalysis() != null
                && input.staticAnalysis().hardCodedSuspects() != null
                && !input.staticAnalysis().hardCodedSuspects().isEmpty()) {
            String note = buildHardCodeNote(tcRaw, oopRaw, input.staticAnalysis().hardCodedSuspects());
            return new QuestionScore(questionNumber, maxScore,
                    tcRaw, oopRaw,   // keep raw scores for transparency in the note
                    BigDecimal.ZERO, true, note, passed, total);
        }

        // 2d. FailIfOopViolated — override to 0 if AI says code fundamentally violates OOP
        if (Boolean.TRUE.equals(config.getFailIfOopViolated())
                && input.aiResult() != null
                && !input.aiResult().aiError()
                && input.aiResult().oopViolated()) {
            String note = buildGuardNote("FailIfOopViolated", tcRaw, oopRaw);
            return new QuestionScore(questionNumber, maxScore,
                    tcRaw, oopRaw,   // keep raw for transparency
                    BigDecimal.ZERO, true, note, passed, total);
        }

        // 2e. FailIfZeroTestCase — override to 0 if not a single test case passed
        if (Boolean.TRUE.equals(config.getFailIfZeroTestCase()) && passed == 0 && total > 0) {
            String note = buildGuardNote("FailIfZeroTestCase", tcRaw, oopRaw);
            return new QuestionScore(questionNumber, maxScore,
                    tcRaw, oopRaw,
                    BigDecimal.ZERO, true, note, passed, total);
        }

                // 2d. Apply grading-mode weights to per-question score.
                // This ensures AnswerScore of each question reflects the selected mode
                // (e.g. MODE_2 = 50% TC + 50% OOP) instead of raw unweighted sum.
                BigDecimal tcWeight  = config.getTestCaseWeight().divide(new BigDecimal("100"), 4, ROUNDING);
                BigDecimal oopWeight = config.getOopWeight().divide(new BigDecimal("100"), 4, ROUNDING);

                BigDecimal weightedTc  = tcRaw.multiply(tcWeight);
                BigDecimal weightedOop = Boolean.TRUE.equals(config.getOopCommentOnly())
                                ? BigDecimal.ZERO
                                : oopRaw.multiply(oopWeight);

                BigDecimal finalQ = weightedTc.add(weightedOop).setScale(SCALE, ROUNDING);

        return new QuestionScore(questionNumber, maxScore,
                tcRaw, oopRaw, finalQ, false, null, passed, total);
    }


    // ─── RAW SCORE HELPERS ────────────────────────────────────────────────────

    /**
     * Raw TC score for a question:
     * {@code (passedCount / totalCount) × maxScore}, or 0 if no test cases.
     */
    private BigDecimal calculateTcRaw(int passed, int total, BigDecimal maxScore) {
        if (total == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(passed)
                .divide(BigDecimal.valueOf(total), 6, ROUNDING)
                .multiply(maxScore)
                .setScale(SCALE, ROUNDING);
    }

        /**
         * Raw OOP score for a question:
         * {@code aiOopScore} (AI prompt now returns score directly on this question's maxScore scale).
         * Returns 0 if AI evaluation failed (graceful degradation).
         */
    private BigDecimal calculateOopRaw(AIReviewResult ai, BigDecimal maxScore) {
        if (ai == null || ai.aiError() || ai.oopScore() == null) {
            return BigDecimal.ZERO;
        }
                BigDecimal safeMax = maxScore != null ? maxScore : BigDecimal.ZERO;
                return ai.oopScore()
                                .max(BigDecimal.ZERO)
                                .min(safeMax)
                                .setScale(SCALE, ROUNDING);
    }

    // ─── NOTE BUILDERS ────────────────────────────────────────────────────────

    private String buildGuardNote(String ruleName, BigDecimal tcRaw, BigDecimal oopRaw) {
        return switch (ruleName) {
            case "FailIfOopViolated" ->
                    "Điểm gốc: TC=%s, OOP=%s → 0đ do vi phạm cấu trúc OOP nghiêm trọng (FailIfOopViolated)"
                            .formatted(tcRaw.toPlainString(), oopRaw.toPlainString());
            case "FailIfZeroTestCase" ->
                    "Điểm gốc: TC=%s, OOP=%s → 0đ do tất cả test case đều FAIL (FailIfZeroTestCase)"
                            .formatted(tcRaw.toPlainString(), oopRaw.toPlainString());
            default -> "Điểm gốc: TC=%s, OOP=%s → 0đ (Guard rule: %s)"
                    .formatted(tcRaw.toPlainString(), oopRaw.toPlainString(), ruleName);
        };
    }

    /**
     * Builds the guard note for the FailIfHardCoded rule.
     * Lists every detected hardcoded value so the student and staff can see exactly
     * what was flagged: e.g. "Điểm gốc: TC=3.00, OOP=2.00 → 0đ do phát hiện
     * giá trị hardcode: [return 42, return \"Hello\", System.out.println(\"abc\")]"
     */
    private String buildHardCodeNote(BigDecimal tcRaw, BigDecimal oopRaw,
                                     List<String> hardCodedValues) {
        String valueList = String.join(", ", hardCodedValues);
        return "Điểm gốc: TC=%s, OOP=%s → 0đ do phát hiện giá trị hardcode: [%s] (FailIfHardCoded)"
                .formatted(tcRaw.toPlainString(), oopRaw.toPlainString(), valueList);
    }

    private String buildTamperNote(BigDecimal tcRaw, BigDecimal oopRaw, String detail) {
        return "Điểm gốc: TC=%s, OOP=%s → 0đ do phát hiện sửa file đề (%s)"
                .formatted(tcRaw.toPlainString(), oopRaw.toPlainString(),
                        detail != null ? detail : "checksum mismatch");
    }

    // ─── INPUT DATA CLASS ─────────────────────────────────────────────────────

    /**
     * Input data for scoring a single question/answer.
     *
     * @param maxScore             maximum score for this question
     * @param passTcCount          number of test cases that passed
     * @param totalTcCount         total number of test cases for this question
     * @param aiResult             AI OOP review result (may be null or have aiError=true)
     * @param hasExamFileTampering true if exam .class files were tampered with
     * @param tamperDetail         description of which files were tampered
     * @param missingFile          true if student did not submit required files
     * @param missingDetail        description of missing files
     * @param staticAnalysis       [NEW] pre-computed static analysis from JavaParser + Reflection;
     *                             used directly for hardcode detection (no AI dependency)
     */
    public record QuestionInput(
            BigDecimal maxScore,
            int passTcCount,
            int totalTcCount,
            AIReviewResult aiResult,
            boolean hasExamFileTampering,
            String tamperDetail,
            boolean missingFile,
            String missingDetail,
            StaticAnalysisResult staticAnalysis  // [NEW] JavaParser + Reflection result
    ) {
        /** Convenience constructor when no tampering, no missing file, and AI result is ready. */
        public static QuestionInput of(BigDecimal maxScore,
                                       int passTcCount, int totalTcCount,
                                       AIReviewResult aiResult,
                                       StaticAnalysisResult staticAnalysis) {  // [NEW]
            return new QuestionInput(maxScore, passTcCount, totalTcCount,
                    aiResult, false, null, false, null, staticAnalysis);
        }

        // [OLD] public static QuestionInput of(BigDecimal maxScore,
        //                                      int passTcCount, int totalTcCount,
        //                                      AIReviewResult aiResult) {
        //     return new QuestionInput(maxScore, passTcCount, totalTcCount,
        //             aiResult, false, null, false, null);
        // }

        /** Convenience constructor for tampered submission. */
        public static QuestionInput tampered(BigDecimal maxScore,
                                             int passTcCount, int totalTcCount,
                                             String detail) {
            return new QuestionInput(maxScore, passTcCount, totalTcCount,
                    null, true, detail, false, null, null);
        }

        /** Convenience constructor for missing file (no jar or no src). */
        public static QuestionInput missing(BigDecimal maxScore, int totalTcCount, String detail) {
            return new QuestionInput(maxScore, 0, totalTcCount,
                    null, false, null, true, detail, null);
        }
    }
}
