package agsfjope.backend.application.dtos.responses.wallet;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response khi tạo lệnh nạp tiền vào ví.
 * Trả về QR code và link thanh toán PayOS.
 * Nếu PayOS timeout (dev), qrCodeUrl/checkoutUrl = null nhưng payosOrderId vẫn có.
 */
@Data
@Builder
public class DepositResponse {
    private UUID depositPaymentId;
    /** orderCode PayOS — dùng để simulate khi PayOS không accessible */
    private String payosOrderId;
    private BigDecimal amount;
    private String currency;
    private String qrCodeUrl;
    private String checkoutUrl;
    private OffsetDateTime expiresAt;
    /** Chỉ có giá trị khi PayOS timeout, hướng dẫn cách simulate */
    private String payosError;
}
