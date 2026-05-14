package agsfjope.backend.application.dtos.responses.grading;

import agsfjope.backend.core.entities.GradingCriteria;
import agsfjope.backend.core.enums.CriterionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a single GradingCriteria.
 * Returned by GET list, POST create, PUT update, and PUT batch endpoints.
 */
@Data
@Builder
public class GradingCriteriaResponse {

    private UUID criteriaId;
    private UUID questionId;
    private String criteriaCode;
    private String criteriaGroup;
    private CriterionType criterionType;
    private String description;
    private BigDecimal maxScore;
    private String checkParamsJson;
    private Integer displayOrder;
    private OffsetDateTime createdAt;

    /** Maps a GradingCriteria entity to its response DTO. */
    public static GradingCriteriaResponse from(GradingCriteria c) {
        return GradingCriteriaResponse.builder()
                .criteriaId(c.getCriteriaId())
                .questionId(c.getQuestion() != null ? c.getQuestion().getQuestionId() : null)
                .criteriaCode(c.getCriteriaCode())
                .criteriaGroup(c.getCriteriaGroup())
                .criterionType(c.getCriterionType())
                .description(c.getDescription())
                .maxScore(c.getMaxScore())
                .checkParamsJson(c.getCheckParamsJson())
                .displayOrder(c.getDisplayOrder())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
