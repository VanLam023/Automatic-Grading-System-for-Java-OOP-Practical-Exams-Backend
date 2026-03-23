package agsfjope.backend.core.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "Answers", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"SubmissionID", "QuestionID"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "AnswerID")
    private UUID answerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SubmissionID", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "QuestionID", nullable = false)
    private Question question;

    @Column(name = "JarFilePath", columnDefinition = "TEXT")
    private String jarFilePath;

    @Column(name = "SourceCodePath", columnDefinition = "TEXT")
    private String sourceCodePath;

    @Column(name = "AnswerScore", precision = 6, scale = 2)
    private BigDecimal answerScore;

    @Column(name = "GuardRuleTriggered", nullable = false)
    @Builder.Default
    private boolean guardRuleTriggered = false;

    @Column(name = "GuardRuleNote", columnDefinition = "TEXT")
    private String guardRuleNote;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
