package agsfjope.backend.core.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "Questions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"ExamPaperID", "QuestionNumber"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "QuestionID")
    private UUID questionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ExamPaperID", nullable = false)
    private ExamPaper examPaper;

    @Column(name = "QuestionNumber", nullable = false)
    private Integer questionNumber;

    @Column(name = "Title", nullable = false, length = 255)
    private String title;

    @Column(name = "Description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "MaxScore", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore;

    /**
     * If true, all whitespace characters are stripped from the INPUT data
     * before feeding it into the student's program during grading.
     * Parsed from {@code REMOVE_SPACES} flag in the exam paper's {@code tc{n}.txt}.
     */
    @Column(name = "RemoveSpaces", nullable = false)
    @Builder.Default
    private Boolean removeSpaces = true;

    /**
     * If true, the comparison between the student's actual output and the expected output
     * is case-sensitive. If false, comparison is case-insensitive.
     * Parsed from {@code CASE_SENSITIVE} flag in the exam paper's {@code tc{n}.txt}.
     */
    @Column(name = "CaseSensitive", nullable = false)
    @Builder.Default
    private Boolean caseSensitive = true;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
