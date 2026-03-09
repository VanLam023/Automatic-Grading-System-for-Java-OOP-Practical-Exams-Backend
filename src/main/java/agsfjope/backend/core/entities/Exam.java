package agsfjope.backend.core.entities;

import agsfjope.backend.core.enums.ExamStatus;
import agsfjope.backend.core.enums.GradingMode;
import jakarta.persistence.*;
import lombok.*;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false)
    @Builder.Default
    private ExamStatus status = ExamStatus.UPCOMING;

    @Enumerated(EnumType.STRING)
    @Column(name = "GradingMode", nullable = false)
    @Builder.Default
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
