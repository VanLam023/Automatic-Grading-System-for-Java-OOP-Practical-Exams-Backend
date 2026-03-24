package agsfjope.backend.core.entities;

import agsfjope.backend.core.enums.TestCaseStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "TestCaseResults", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"AnswerID", "TestCaseID"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCaseResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "TestCaseResultID")
    private UUID testCaseResultId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AnswerID", nullable = false)
    private Answer answer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TestCaseID", nullable = false)
    private TestCase testCase;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "Status", nullable = false, columnDefinition = "test_case_status")
    private TestCaseStatus status;

    @Column(name = "ActualOutput", columnDefinition = "TEXT")
    private String actualOutput;

    @Column(name = "ExecutionTimeMs")
    private Integer executionTimeMs;

    @Column(name = "ErrorMessage", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "ScoreEarned", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal scoreEarned = BigDecimal.ZERO;

    @Column(name = "ExecutedAt", nullable = false, updatable = false)
    private OffsetDateTime executedAt;

    @PrePersist
    protected void onCreate() {
        if (executedAt == null) {
            executedAt = OffsetDateTime.now();
        }
    }
}
