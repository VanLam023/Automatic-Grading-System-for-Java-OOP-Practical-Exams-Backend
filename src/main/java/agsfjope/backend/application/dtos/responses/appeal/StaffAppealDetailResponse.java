package agsfjope.backend.application.dtos.responses.appeal;

import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Chi tiết đầy đủ một đơn phúc khảo — dùng cho màn hình Appeal Detail (Exam Staff).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffAppealDetailResponse {

    // ─── Appeal ──────────────────────────────────────────────────────────────
    private UUID appealId;
    private String appealCode;
    private AppealStatus status;
    private String reason;
    private String lecturerComment;
    private BigDecimal originalScore;
    private BigDecimal newScore;
    private java.util.Map<String, BigDecimal> newQuestionScores;
    private OffsetDateTime createdAt;
    private OffsetDateTime deadlineAt;
    private OffsetDateTime completedAt;

    // ─── Student ─────────────────────────────────────────────────────────────
    private UUID studentId;
    private String studentName;
    private String studentMssv;
    private String studentEmail;

    // ─── Exam / Block ─────────────────────────────────────────────────────────
    private String examName;
    private String semester;
    private String blockName;

    // ─── Submission ──────────────────────────────────────────────────────────
    private UUID submissionId;
    private String submissionFileName;

    // ─── Assigned Lecturer ───────────────────────────────────────────────────
    private UUID assignedLecturerId;
    private String assignedLecturerName;
    private String assignedLecturerEmail;
    private OffsetDateTime assignedAt;
    private String assignedByName;

    // ─── Payment ─────────────────────────────────────────────────────────────
    private BigDecimal paymentAmount;
    private PaymentStatus paymentStatus;
    private OffsetDateTime paidAt;
}
