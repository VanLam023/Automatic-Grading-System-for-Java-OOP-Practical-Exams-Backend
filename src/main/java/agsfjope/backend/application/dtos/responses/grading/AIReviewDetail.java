package agsfjope.backend.application.dtos.responses.grading;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Detail of the AI OOP review for one answer — included inside {@link AnswerGradingDetail}.
 */
@Data
@Builder
public class AIReviewDetail {

    private UUID aiReviewId;

    /** Total OOP score on the question's own maxScore scale. */
    private BigDecimal oopScore;

    /** Legacy fixed breakdown fields — kept for backward compatibility. */
    private BigDecimal encapsulationScore;
    private BigDecimal inheritanceScore;
    private BigDecimal polymorphismScore;
    private BigDecimal designQualityScore;
    private BigDecimal codeIntegrityScore;

    /** Dynamic rubric breakdown — new source of truth for rubric-based grading. */
    private List<Map<String, Object>> criteriaResults;

    /** List of specific OOP violations found. */
    private List<String> violations;

    /** List of hard-coded values detected. */
    private List<String> hardCodedValues;

    /** Full AI review comment in the configured language. */
    private String comment;

    /** Parsing / provider error info, useful when a submission is marked GRADING_FAILED. */
    private boolean aiError;
    private String errorMessage;

    /** True if the submission fundamentally violates OOP principles. */
    private boolean oopViolated;
}
