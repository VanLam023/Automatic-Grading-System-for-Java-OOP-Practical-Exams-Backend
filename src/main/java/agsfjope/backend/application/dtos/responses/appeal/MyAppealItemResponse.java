package agsfjope.backend.application.dtos.responses.appeal;

import agsfjope.backend.core.enums.AppealStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO cho mỗi đơn phúc khảo trong trang "Phúc khảo của tôi" (Student).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyAppealItemResponse {

    /** UUID đơn phúc khảo. */
    private UUID appealId;

    /** UUID bài nộp được phúc khảo. */
    private UUID submissionId;

    /** Mã yêu cầu phúc khảo (hiển thị dạng #PK-2023-xxxx). */
    private String appealCode;

    /** Tên bài thi. */
    private String examName;

    /** Học kỳ. */
    private String semester;

    /** Trạng thái đơn: PENDING_PAYMENT, PENDING, PROCESSING, APPROVED, DENIED, CANCELLED. */
    private AppealStatus status;

    /** Điểm gốc (trước phúc khảo). */
    private BigDecimal originalScore;

    /** Điểm sau phúc khảo (null nếu chưa chấm lại). */
    private BigDecimal newScore;

    /** Điểm chấm lại theo từng câu (nếu giảng viên đã chấm lại từng câu). */
    private Map<String, BigDecimal> newQuestionScores;

    /** Lý do phúc khảo của sinh viên. */
    private String reason;

    /** Phản hồi từ giảng viên (null nếu chưa có). */
    private String lecturerComment;

    /** Tên giảng viên được phân công (null nếu chưa phân công). */
    private String assignedLecturerName;

    /** Ngày tạo đơn. */
    private OffsetDateTime createdAt;

    /** Ngày hoàn thành (null nếu chưa hoàn thành). */
    private OffsetDateTime completedAt;

    /** Deadline chấm phúc khảo (null nếu chưa phân công). */
    private OffsetDateTime deadlineAt;
}
