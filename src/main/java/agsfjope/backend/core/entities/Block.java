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

    /** Ngày diễn ra thi của block này (null nếu chưa lên lịch) */
    @Column(name = "ExamDate", nullable = true)
    private LocalDate examDate;

    /** Thời gian bắt đầu làm bài thi (null nếu chưa lên lịch) */
    @Column(name = "StartTime", nullable = true)
    private OffsetDateTime startTime;

    /** Thời gian kết thúc làm bài thi (null nếu chưa lên lịch). CHK_BlockTime: EndTime > StartTime khi cả hai đều được set */
    @Column(name = "EndTime", nullable = true)
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
