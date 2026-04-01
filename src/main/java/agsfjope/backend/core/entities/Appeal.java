package agsfjope.backend.core.entities;

import agsfjope.backend.core.enums.AppealStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "Appeals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appeal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "AppealID")
    private UUID appealId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SubmissionID", nullable = false, unique = true)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StudentID", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AssignedLecturerID")
    private User assignedLecturer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AssignedBy")
    private User assignedBy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "Status", nullable = false, columnDefinition = "appeal_status")
    @Builder.Default
    private AppealStatus status = AppealStatus.PENDING_PAYMENT;

    @Column(name = "Reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "LecturerComment", columnDefinition = "TEXT")
    private String lecturerComment;

    @Column(name = "NewScore", precision = 6, scale = 2)
    private BigDecimal newScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "NewQuestionScores", columnDefinition = "jsonb")
    private java.util.Map<String, BigDecimal> newQuestionScores;

    @Column(name = "DeadlineAt")
    private OffsetDateTime deadlineAt;

    @Column(name = "AssignedAt")
    private OffsetDateTime assignedAt;

    @Column(name = "CompletedAt")
    private OffsetDateTime completedAt;

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
