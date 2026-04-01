package agsfjope.backend.application.dtos.responses.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO trả về thông tin thanh toán phúc khảo cho client.
 * <p>
 * Được nhúng bên trong {@code AppealResponse} (field {@code payment}) để
 * Frontend có thể hiển thị QR code và link thanh toán ngay sau khi
 * sinh viên tạo đơn phúc khảo thành công.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    /** UUID định danh giao dịch trong hệ thống. */
    private UUID paymentId;

    /** Số tiền thanh toán (đơn vị: VND). */
    private BigDecimal amount;

    /** Loại tiền tệ (mặc định "VND"). */
    private String currency;

    /** Trạng thái giao dịch: PENDING / SUCCESS / FAILED / REFUNDED. */
    private String status;

    /**
     * URL hình ảnh QR code do PayOS cấp.
     * Frontend hiển thị QR code này để sinh viên quét thanh toán.
     */
    private String qrCodeUrl;

    /**
     * URL trang thanh toán PayOS (dùng thay thế khi không quét được QR).
     */
    private String checkoutUrl;

    /**
     * Thời điểm hết hạn thanh toán.
     * Đọc từ {@code PAYOS_PAYMENT_TIMEOUT_MINUTES} trong SystemConfigs.
     * Sau thời điểm này, Scheduler sẽ tự động hủy đơn (BR-33).
     */
    private OffsetDateTime expiresAt;
}
