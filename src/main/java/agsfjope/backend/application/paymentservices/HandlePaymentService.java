package agsfjope.backend.application.paymentservices;

import agsfjope.backend.application.dtos.requests.payment.PayOSWebhookRequest;
import agsfjope.backend.application.dtos.responses.payment.PaymentResponse;

import java.util.UUID;

/**
 * Input port interface cho các luồng xử lý thanh toán PayOS.
 * <p>
 * Định nghĩa hai luồng chính:
 * <ol>
 *   <li>Xử lý webhook callback từ PayOS (khi giao dịch thành công/thất bại)</li>
 *   <li>Dọn dẹp các giao dịch PENDING đã hết hạn timeout (15 phút)</li>
 * </ol>
 * Tầng Infrastructure ({@code HandlePaymentServiceImpl}) implement interface này.
 * {@code AppealController} và {@code PaymentTimeoutScheduler} gọi thông qua interface.
 * </p>
 */
public interface HandlePaymentService {

    /**
     * Xử lý webhook callback từ PayOS sau khi giao dịch hoàn tất.
     * <p>
     * Luồng xử lý:
     * <ol>
     *   <li>Verify checksum HMAC-SHA256 (trả về HTTP 400 nếu sai)</li>
     *   <li>Tìm Payment theo orderCode</li>
     *   <li>Nếu PAID: cập nhật Payment → SUCCESS, gửi email xác nhận</li>
     *   <li>Nếu CANCELLED: cập nhật Payment → FAILED</li>
     *   <li>TODO (Appeal): cập nhật Appeal status tương ứng</li>
     * </ol>
     * </p>
     *
     * @param webhookRequest dữ liệu webhook nhận từ PayOS
     */
    /**
     * Xử lý webhook callback từ PayOS sau khi giao dịch hoàn tất (Dùng rawBody string để verify checksum chính xác nhất).
     */
    void handleWebhook(String rawBody);

    /**
     * Bản dùng DTO (để tương thích).
     */
    void handleWebhook(PayOSWebhookRequest webhookRequest);

    /**
     * Retry tạo lại link thanh toán cho Appeal bị lỗi kỹ thuật.
     * <p>
     * Chỉ cho phép retry khi Payment vẫn ở trạng thái PENDING và chưa hết hạn.
     * Hủy link PayOS cũ, tạo link mới, cập nhật Payment record.
     * </p>
     *
     * @param appealId UUID của Appeal cần tạo lại payment
     * @return thông tin payment mới với QR code và checkout URL
     */
    PaymentResponse retryPayment(UUID appealId);

    /**
     * Quét và xử lý tất cả Payment PENDING đã quá hạn timeout.
     * <p>
     * Gọi bởi {@code PaymentTimeoutScheduler} mỗi 1 phút.
     * Với mỗi Payment hết hạn:
     * <ol>
     *   <li>Hủy link PayOS (nếu còn)</li>
     *   <li>Cập nhật Payment → FAILED</li>
     *   <li>TODO (Appeal): cập nhật Appeal → CANCELLED</li>
     * </ol>
     * </p>
     */
    void handleExpiredPayments();
}
