package agsfjope.backend.core.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Result of evaluating one {@link GradingCriteria} against one {@link Answer}.
 * Persisted to the {@code criteria_results} table.
 *
 * <p>Mirrors the pattern of {@link TestCaseResult} (1 Answer → N rows).
 * OOP score for an answer = SUM(earnedScore) of its criteria_results.</p>
 */
@Entity
@Table(
    name = "criteria_results",
    uniqueConstraints = @UniqueConstraint(columnNames = {"answer_id", "criteria_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CriteriaResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "criteria_result_id")
    private UUID criteriaResultId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    private Answer answer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criteria_id", nullable = false)
    private GradingCriteria criteria;

    /** True if the criterion was fully satisfied. */
    @Column(name = "passed", nullable = false)
    @Builder.Default
    private boolean passed = false;

    /** Points awarded. 0 if failed. May be partial if criterion splits credit. */
    @Column(name = "earned_score", nullable = false, precision = 6, scale = 4)
    @Builder.Default
    private BigDecimal earnedScore = BigDecimal.ZERO;

    /**
     * Human-readable explanation of the check outcome.
     * E.g. "❌ Field 'name' is public, expected private"
     *      "✅ Class 'Product' exists"
     */
    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private OffsetDateTime executedAt;

    @PrePersist
    protected void onCreate() {
        if (executedAt == null) executedAt = OffsetDateTime.now();
    }
}
