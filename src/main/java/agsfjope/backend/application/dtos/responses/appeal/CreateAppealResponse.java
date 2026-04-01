package agsfjope.backend.application.dtos.responses.appeal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response trả về sau khi sinh viên tạo đơn phúc khảo thành công.
 *
 * <p>Luồng mới: sinh viên dùng tiền trong ví để thanh toán.
 * Không cần QR code / PayOS nữa — appeal chuyển thẳng sang PENDING.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAppealResponse {

    // ─── Appeal Info ─────────────────────────────────────────────────────────

    /** UUID đơn phúc khảo vừa được tạo. */
    private UUID appealId;

    /** UUID bài nộp liên quan. */
    private UUID submissionId;

    /** Tên bài thi cần phúc khảo. */
    private String examName;

    /** Điểm gốc của bài nộp (trước phúc khảo). */
    private BigDecimal originalScore;

    // ─── Payment Info (Wallet) ────────────────────────────────────────────────

    /** Phí phúc khảo đã bị trừ từ ví (VND). */
    private BigDecimal amount;

    /** Số dư ví còn lại sau khi thanh toán. */
    private BigDecimal walletBalanceAfter;
}
