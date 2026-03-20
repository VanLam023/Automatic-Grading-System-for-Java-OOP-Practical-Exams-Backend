package agsfjope.backend.core.entities;

import agsfjope.backend.core.enums.SubmissionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "Submissions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"StudentID", "BlockID"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "SubmissionID")
    private UUID submissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StudentID", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BlockID", nullable = false)
    private Block block;

    @Column(name = "FileName", nullable = false, length = 255)
    private String fileName;

    @Column(name = "FilePath", nullable = false, columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "FileSizeBytes", nullable = false)
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "Status", nullable = false, columnDefinition = "submission_status")
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.SUBMITTED;

    @Column(name = "SubmittedAt", nullable = false, updatable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "GradedAt")
    private OffsetDateTime gradedAt;

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) {
            submittedAt = OffsetDateTime.now();
        }
    }
}
