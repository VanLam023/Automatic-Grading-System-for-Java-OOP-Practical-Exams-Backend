package agsfjope.backend.core.entities;

import agsfjope.backend.core.enums.GradingMode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "GradingResults")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "GradingResultID")
    private UUID gradingResultId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SubmissionID", nullable = false, unique = true)
    private Submission submission;

    @Enumerated(EnumType.STRING)
    @Column(name = "GradingMode", nullable = false)
    private GradingMode gradingMode;

    @Column(name = "TotalScore", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal totalScore = BigDecimal.ZERO;

    @Column(name = "MaxScore", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxScore;

    @Column(name = "TestCaseScore", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal testCaseScore = BigDecimal.ZERO;

    @Column(name = "OopScore", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal oopScore = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GradedBy")
    private User gradedBy;

    @Column(name = "Note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "UpdatedAt", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
