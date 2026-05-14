package agsfjope.backend.application.dtos.responses.grading;

import agsfjope.backend.core.enums.GradingMode;
import agsfjope.backend.core.enums.GradingResultStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full grading result for a single submission.
 * Returned to both STUDENT (to see their result) and EXAM_STAFF (for review).
 */
@Data
@Builder
public class GradingResultResponse {

    private UUID gradingResultId;
    private UUID submissionId;
    private UUID studentId;
    private String studentName;
    private String studentCode;
    private String studentEmail;
    private String semesterName;
    private String blockName;
    private String academicYear;

    private GradingMode gradingMode;

    /**
     * Overall pass/fail status.
     * PASS = totalScore >= 4.0, FAIL otherwise.
     */
    private GradingResultStatus status;

    private BigDecimal totalScore;
    private BigDecimal maxScore;

    /** Weighted test case score component. */
    private BigDecimal testCaseScore;

    /** Weighted OOP score component. */
    private BigDecimal oopScore;

    /** Global note — e.g., re-grading notes, or global violations. */
    private String note;

    private OffsetDateTime gradedAt;

    /** Per-question grading breakdown. */
    private List<AnswerGradingDetail> answers;
}
