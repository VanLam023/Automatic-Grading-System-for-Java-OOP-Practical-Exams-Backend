package agsfjope.backend.application.dtos.responses.grading;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Detail of a single OOP grading criterion evaluation for a given answer.
 * Included inside {@link AnswerGradingDetail#criteriaResults}.
 */
@Data
@Builder
public class CriteriaResultDetail {

    private UUID criteriaResultId;

    /** Human-readable code, e.g. Q1.1 */
    private String criteriaCode;

    /** Type of criterion e.g. FIELD_CHECK, METHOD_SIGNATURE, … */
    private String criterionType;

    /** Human-readable description shown to lecturer/student. */
    private String description;

    /** Maximum points this criterion can award. */
    private BigDecimal maxScore;

    /** Whether the criterion was satisfied. */
    private boolean passed;

    /** Points actually awarded (0 if failed). */
    private BigDecimal earnedScore;

    /**
     * Human-readable explanation of why the criterion passed or failed.
     * E.g. "✅ Class 'Product' exists"
     *      "❌ Field 'name' is public, expected private"
     */
    private String feedback;
}
