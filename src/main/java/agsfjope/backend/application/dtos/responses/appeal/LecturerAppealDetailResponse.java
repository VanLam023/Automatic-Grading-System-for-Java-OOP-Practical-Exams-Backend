package agsfjope.backend.application.dtos.responses.appeal;

import agsfjope.backend.core.enums.AppealStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LecturerAppealDetailResponse {
    private UUID appealId;
    private String appealCode;
    private AppealStatus status;
    private String reason;
    private String lecturerComment;

    // Scores
    private BigDecimal originalScore;
    private BigDecimal newScore;
    private java.util.Map<String, BigDecimal> newQuestionScores;
    private BigDecimal testCaseScore;
    private BigDecimal oopScore;

    // Dates
    private OffsetDateTime createdAt;
    private OffsetDateTime deadlineAt;

    // Student
    private String studentName;
    private String studentMssv;

    // Exam / Block
    private String examName;
    private String semester;
    private String blockName;

    // Submission
    private UUID submissionId;
    private String submissionFileName;
}
