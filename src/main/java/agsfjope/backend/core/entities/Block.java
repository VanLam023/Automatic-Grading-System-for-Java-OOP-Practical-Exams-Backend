package agsfjope.backend.core.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "Blocks", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"ExamID", "Name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "BlockID")
    private UUID blockId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ExamID", nullable = false)
    private Exam exam;

    @Column(name = "Name", nullable = false, length = 50)
    private String name;

    @Column(name = "Description", columnDefinition = "TEXT")
    private String description;

    /** Ngày diễn ra thi của block này */
    @Column(name = "ExamDate", nullable = false)
    private LocalDate examDate;

    /** Thời gian bắt đầu làm bài thi */
    @Column(name = "StartTime", nullable = false)
    private OffsetDateTime startTime;

    /** Thời gian kết thúc làm bài thi (CHK_BlockTime: EndTime > StartTime) */
    @Column(name = "EndTime", nullable = false)
    private OffsetDateTime endTime;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
