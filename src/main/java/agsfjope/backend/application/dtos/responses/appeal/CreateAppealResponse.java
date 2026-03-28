package agsfjope.backend.application.dtos.responses.appeal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response trả về sau khi sinh viên tạo đơn phúc khảo thành công.
 *
 * <p>Bao gồm thông tin appeal và link thanh toán PayOS để Frontend
 * hiển thị QR code / checkout URL ngay lập tức (màn hình Payment(Student)).</p>
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
    private java.math.BigDecimal originalScore;

    // ─── Payment Info ─────────────────────────────────────────────────────────

    /** UUID giao dịch thanh toán trong hệ thống. */
    private UUID paymentId;

    /** Phí phúc khảo (VND). */
    private BigDecimal amount;

    /** Loại tiền tệ (mặc định "VND"). */
    private String currency;

    /**
     * Mã đơn hàng PayOS (epoch seconds) — dùng để tra cứu khi webhook callback.
     */
    private String payosOrderId;

    /**
     * URL hình ảnh QR code do PayOS cấp.
     * Frontend hiển thị để sinh viên quét thanh toán.
     */
    private String qrCodeUrl;

    /**
     * URL trang thanh toán PayOS.
     * Dùng thay thế khi không quét được QR.
     */
    private String checkoutUrl;

    /**
     * Thời điểm hết hạn thanh toán (15 phút kể từ lúc tạo).
     * Sau thời điểm này, Scheduler tự động hủy đơn.
     */
    private OffsetDateTime expiresAt;
}
