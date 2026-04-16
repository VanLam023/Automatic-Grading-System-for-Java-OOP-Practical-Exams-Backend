package agsfjope.backend.domain.grading;

import agsfjope.backend.core.entities.GradingModeConfig;
import agsfjope.backend.infrastructure.ai.AIReviewResult;
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
 *   passed     = finalScore >= 4.0
 * </pre>
 *
 * <p>Guard rules force the QUESTION score to 0 but the global weight-based calculation
 * still uses the (effectively 0) contribution from that question.</p>
 */
@Slf4j
@Component
public class ScoreCalculator {

    private static final BigDecimal PASS_THRESHOLD  = BigDecimal.ZERO;
    private static final int        SCALE            = 2;
    private static final RoundingMode ROUNDING       = RoundingMode.HALF_UP;

    /**
     * Calculates the full grading result for a submission.
     *
     * @param config          the grading mode config (weights + guard rules)
     * @param questionInputs  per-question data keyed by question number
     * @return FinalGradingScore with per-question breakdown and final totals
     */
    public FinalGradingScore calculate(GradingModeConfig config,
                                       Map<Integer, QuestionInput> questionInputs) {

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

        // Apply weights
        BigDecimal tcWeight  = config.getTestCaseWeight().divide(new BigDecimal("100"), 4, ROUNDING);
        BigDecimal oopWeight = config.getOopWeight().divide(new BigDecimal("100"), 4, ROUNDING);

        BigDecimal tcTotal   = sumTcRaw.multiply(tcWeight).setScale(SCALE, ROUNDING);
        BigDecimal oopTotal  = sumOopRaw.multiply(oopWeight).setScale(SCALE, ROUNDING);
        BigDecimal finalScore = tcTotal.add(oopTotal).setScale(SCALE, ROUNDING);

        boolean passed = finalScore.compareTo(PASS_THRESHOLD) > 0;

        String globalNote = null;
        if (config.getOopCommentOnly()) {
            globalNote = "Chế độ: OOP chỉ nhận xét, không tính điểm.";
        }

        log.debug("Score calculated: tc={}, oop={}, final={}, passed={}",
                tcTotal, oopTotal, finalScore, passed);

        return new FinalGradingScore(questionScores, finalScore, tcTotal, oopTotal, passed, globalNote);
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

        // 2b. FailIfOopViolated — override to 0 if AI says code fundamentally violates OOP
        if (Boolean.TRUE.equals(config.getFailIfOopViolated())
                && input.aiResult() != null
                && !input.aiResult().aiError()
                && input.aiResult().oopViolated()) {
            String note = buildGuardNote("FailIfOopViolated", tcRaw, oopRaw);
            return new QuestionScore(questionNumber, maxScore,
                    tcRaw, oopRaw,   // keep raw for transparency
                    BigDecimal.ZERO, true, note, passed, total);
        }

        // 2c. FailIfZeroTestCase — override to 0 if not a single test case passed
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
     */
    public record QuestionInput(
            BigDecimal maxScore,
            int passTcCount,
            int totalTcCount,
            AIReviewResult aiResult,
            boolean hasExamFileTampering,
            String tamperDetail,
            boolean missingFile,
            String missingDetail
    ) {
        /** Convenience constructor when no tampering, no missing file, and AI result is ready. */
        public static QuestionInput of(BigDecimal maxScore,
                                       int passTcCount, int totalTcCount,
                                       AIReviewResult aiResult) {
            return new QuestionInput(maxScore, passTcCount, totalTcCount,
                    aiResult, false, null, false, null);
        }

        /** Convenience constructor for tampered submission. */
        public static QuestionInput tampered(BigDecimal maxScore,
                                             int passTcCount, int totalTcCount,
                                             String detail) {
            return new QuestionInput(maxScore, passTcCount, totalTcCount,
                    null, true, detail, false, null);
        }

        /** Convenience constructor for missing file (no jar or no src). */
        public static QuestionInput missing(BigDecimal maxScore, int totalTcCount, String detail) {
            return new QuestionInput(maxScore, 0, totalTcCount,
                    null, false, null, true, detail);
        }
    }
}
