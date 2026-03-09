package agsfjope.backend.core.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "TestCases", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"QuestionID", "TestCaseNumber"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "TestCaseID")
    private UUID testCaseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "QuestionID", nullable = false)
    private Question question;

    @Column(name = "TestCaseNumber", nullable = false)
    private Integer testCaseNumber;

    @Column(name = "InputData", columnDefinition = "TEXT")
    private String inputData;

    @Column(name = "ExpectedOutput", nullable = false, columnDefinition = "TEXT")
    private String expectedOutput;

    @Column(name = "Score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal score = BigDecimal.ZERO;

    @Column(name = "TimeLimitMs", nullable = false)
    @Builder.Default
    private Integer timeLimitMs = 5000;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
