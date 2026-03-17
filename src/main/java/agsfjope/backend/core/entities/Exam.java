package agsfjope.backend.core.entities;

import agsfjope.backend.core.enums.ExamStatus;
import agsfjope.backend.core.enums.GradingMode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "Exams")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ExamID")
    private UUID examId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CreatedBy", nullable = false)
    private User createdBy;

    @Column(name = "Name", nullable = false, length = 255)
    private String name;

    @Column(name = "Semester", nullable = false, length = 50)
    private String semester;

    @Column(name = "AcademicYear", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "Description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "StartTime", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "EndTime", nullable = false)
    private OffsetDateTime endTime;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "Status", columnDefinition = "exam_status", nullable = false)
    private ExamStatus status = ExamStatus.UPCOMING;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "GradingMode", columnDefinition = "grading_mode")
    private GradingMode gradingMode = GradingMode.MODE_1;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "UpdatedAt", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "DeletedAt")
    private OffsetDateTime deletedAt;

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
