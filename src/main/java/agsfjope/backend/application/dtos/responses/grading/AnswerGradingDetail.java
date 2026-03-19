package agsfjope.backend.application.dtos.responses.grading;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Grading breakdown for a single answer (question) within a submission.
 * Contains all test case results + the AI OOP review for that answer.
 */
@Data
@Builder
public class AnswerGradingDetail {

    private UUID answerId;
    private int questionNumber;
    private String questionTitle;

    /** Maximum score for this question. */
    private BigDecimal maxScore;

    /** Raw test case score before guard rules applied. */
    private BigDecimal rawTestCaseScore;

    /** Raw OOP score before guard rules applied. */
    private BigDecimal rawOopScore;

    /** Final score for this question (after guard rules applied). */
    private BigDecimal questionScore;

    /**
     * Guard rule note — explains why score was reduced to 0 if applicable.
     * E.g.: "Điểm gốc: TC=6.0, OOP=8.0 → 0đ do vi phạm OOP (FailIfOopViolated)"
     */
    private String guardRuleNote;

    /** Whether a guard rule was triggered (score forced to 0). */
    private boolean guardRuleTriggered;

    /** Detailed results for each test case. */
    private List<TestCaseResultDetail> testCaseResults;

    /** AI OOP review result. */
    private AIReviewDetail aiReview;
}
