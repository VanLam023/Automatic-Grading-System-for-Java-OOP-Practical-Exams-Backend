package agsfjope.backend.application.dtos.responses.submission;

import agsfjope.backend.core.enums.GradingResultStatus;
import agsfjope.backend.core.enums.SubmissionStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Summary DTO for one submission within a block — used by EXAM_STAFF to see
 * the full list (submitted + grading + graded) with optional score overlay.
 *
 * <p>Combines Submission metadata with GradingResult if available.</p>
 */
@Value
@Builder
public class SubmissionListItemResponse {

    // ── Submission ─────────────────────────────────────────────────────────────
    UUID   submissionId;
    String fileName;
    Long   fileSizeBytes;
    SubmissionStatus submissionStatus;   // SUBMITTED | GRADING | GRADED
    OffsetDateTime   submittedAt;

    // ── Student ────────────────────────────────────────────────────────────────
    UUID   studentId;
    String studentName;
    String studentCode;   // MSSV
    String studentEmail;

    // ── Grading (null if not yet graded) ───────────────────────────────────────
    UUID               gradingResultId;
    GradingResultStatus gradingStatus;   // PASS | FAIL — null if not graded
    BigDecimal         totalScore;
    BigDecimal         maxScore;
    OffsetDateTime     gradedAt;
}
