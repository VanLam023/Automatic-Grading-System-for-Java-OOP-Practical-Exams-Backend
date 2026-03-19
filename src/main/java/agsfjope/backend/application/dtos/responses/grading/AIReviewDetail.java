package agsfjope.backend.application.dtos.responses.grading;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Detail of the AI OOP review for one answer — included inside {@link AnswerGradingDetail}.
 */
@Data
@Builder
public class AIReviewDetail {

    private UUID aiReviewId;

    /** Total OOP score (0–10). */
    private BigDecimal oopScore;

    /** Breakdown of each OOP criterion (each 0–2). */
    private BigDecimal encapsulationScore;
    private BigDecimal inheritanceScore;
    private BigDecimal polymorphismScore;
    private BigDecimal designQualityScore;
    private BigDecimal codeIntegrityScore;

    /** List of specific OOP violations found. */
    private List<String> violations;

    /** List of hard-coded values detected. */
    private List<String> hardCodedValues;

    /** Full AI review comment in the configured language. */
    private String comment;

    /** True if the submission fundamentally violates OOP principles. */
    private boolean oopViolated;
}
