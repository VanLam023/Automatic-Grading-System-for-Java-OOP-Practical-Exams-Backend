package agsfjope.backend.application.dtos.requests.grading;

import agsfjope.backend.core.enums.CriterionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for creating or updating a single GradingCriteria.
 * Used by both POST (create) and the batch PUT endpoint.
 */
@Data
public class GradingCriteriaRequest {

    /** Human-readable code, e.g. {@code Q1.1}. Unique per question. */
    @NotBlank(message = "criteriaCode is required")
    @Size(max = 20, message = "criteriaCode must be ≤ 20 characters")
    private String criteriaCode;

    /**
     * Criterion group: {@code STRUCTURAL} (default) or {@code BEHAVIORAL} (reserved).
     * Frontend always sends {@code STRUCTURAL} for OOP checks.
     */
    @Size(max = 20)
    private String criteriaGroup = "STRUCTURAL";

    /** Criterion type — must match a registered {@code CriterionHandler}. */
    @NotNull(message = "criterionType is required")
    private CriterionType criterionType;

    /** Human-readable description displayed to lecturer and student. */
    @NotBlank(message = "description is required")
    private String description;

    /** Maximum points awarded when this criterion passes. Must be ≥ 0. */
    @NotNull(message = "maxScore is required")
    @DecimalMin(value = "0.0", message = "maxScore must be ≥ 0")
    private BigDecimal maxScore;

    /**
     * JSON string of parameters consumed by the matching CriterionHandler.
     * E.g. {@code {"className": "Product"}} for CLASS_EXISTS.
     * Defaults to {@code {}} if not provided.
     */
    private String checkParamsJson = "{}";

    /** UI display order (0-based). Lower = evaluated first. */
    private Integer displayOrder = 0;
}
