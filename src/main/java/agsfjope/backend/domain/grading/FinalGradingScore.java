package agsfjope.backend.domain.grading;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable final grading result across all questions of a submission.
 *
 * @param questionScores  per-question breakdown (one entry per answer)
 * @param totalScore      final weighted score (0–sum of maxScore across questions)
 * @param testCaseScore   weighted test case component of totalScore
 * @param oopScore        weighted OOP component of totalScore
 * @param passed          true if totalScore >= 4.0
 * @param globalNote      any global-level notes (e.g., if AI was unavailable)
 */
public record FinalGradingScore(
        List<QuestionScore> questionScores,
        BigDecimal totalScore,
        BigDecimal testCaseScore,
        BigDecimal oopScore,
        boolean passed,
        String globalNote
) {}
