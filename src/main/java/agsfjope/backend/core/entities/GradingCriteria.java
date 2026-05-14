package agsfjope.backend.core.entities;

import agsfjope.backend.core.enums.CriterionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * One OOP structural sub-criterion for a given Question.
 * Persisted to the {@code grading_criteria} table.
 *
 * <p>The {@link #checkParams} JSONB field holds the engine parameters
 * consumed by the matching {@code CriterionHandler}. The schema varies
 * per {@link #criterionType}; see {@code CriterionType} javadoc for examples.</p>
 */
@Entity
@Table(
    name = "grading_criteria",
    uniqueConstraints = @UniqueConstraint(columnNames = {"question_id", "criteria_code"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradingCriteria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "criteria_id")
    private UUID criteriaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    /** Human-readable code, e.g. {@code Q1.1}. Unique per question. */
    @Column(name = "criteria_code", nullable = false, length = 20)
    private String criteriaCode;

    /** {@code STRUCTURAL} (default) or {@code BEHAVIORAL} (reserved). */
    @Column(name = "criteria_group", nullable = false, length = 20)
    @Builder.Default
    private String criteriaGroup = "STRUCTURAL";

    @Enumerated(EnumType.STRING)
    @Column(name = "criterion_type", nullable = false, length = 50)
    private CriterionType criterionType;

    /** Human-readable description shown to lecturer/student. */
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /** Maximum points this criterion can award. */
    @Column(name = "max_score", nullable = false, precision = 6, scale = 4)
    private BigDecimal maxScore;

    /**
     * JSONB parameters consumed by the matching CriterionHandler.
     * Stored as a JSON string and deserialized on read via
     * {@link agsfjope.backend.infrastructure.grading.criteria.AbstractCriterionHandler}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "check_params", nullable = false, columnDefinition = "jsonb")
    private String checkParamsJson;

    /** Display order for UI rendering (maps to {@code display_order} column). */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    /**
     * Deserializes {@link #checkParamsJson} into a typed map for handler consumption.
     * Returns an empty map if the JSON is null or unparseable.
     */
    @Transient
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCheckParams() {
        if (checkParamsJson == null || checkParamsJson.isBlank()) return Map.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(checkParamsJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
