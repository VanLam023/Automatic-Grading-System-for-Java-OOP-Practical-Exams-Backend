package agsfjope.backend.core.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "AIReviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "AIReviewID")
    private UUID aiReviewId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AnswerID", nullable = false, unique = true)
    private Answer answer;

    @Column(name = "AIModel", nullable = false, length = 100)
    private String aiModel;

    @Column(name = "OopScore", precision = 5, scale = 2)
    private BigDecimal oopScore;

    @Column(name = "Comment", columnDefinition = "TEXT")
    private String comment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "RawResponse", columnDefinition = "JSONB")
    private String rawResponse;

    @Column(name = "IsOopViolated", nullable = false)
    @Builder.Default
    private Boolean isOopViolated = false;

    @Column(name = "TokensUsed")
    private Integer tokensUsed;

    @Column(name = "ReviewedAt", nullable = false, updatable = false)
    private OffsetDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        if (reviewedAt == null) {
            reviewedAt = OffsetDateTime.now();
        }
    }
}
