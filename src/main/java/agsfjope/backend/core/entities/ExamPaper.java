package agsfjope.backend.core.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ExamPapers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamPaper {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ExamPaperID")
    private UUID examPaperId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BlockID", nullable = false, unique = true)
    private Block block;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UploadedBy", nullable = false)
    private User uploadedBy;

    @Column(name = "FileName", nullable = false, length = 255)
    private String fileName;

    @Column(name = "FilePath", nullable = false, columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "FileSizeBytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "TotalQuestions", nullable = false)
    @Builder.Default
    private Integer totalQuestions = 0;

    @Column(name = "TotalTestCases", nullable = false)
    @Builder.Default
    private Integer totalTestCases = 0;

    @Column(name = "UploadedAt", nullable = false, updatable = false)
    private OffsetDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = OffsetDateTime.now();
        }
    }
}
