package agsfjope.backend.application.ports.out;

import agsfjope.backend.application.dtos.requests.payment.PayOSWebhookRequest;

import java.math.BigDecimal;

/**
 * Output port interface cho cổng thanh toán PayOS.
 * <p>
 * Tuân theo Clean Architecture: tầng Application định nghĩa interface này (the "what"),
 * tầng Infrastructure ({@code PayOSAdapter}) sẽ cung cấp implementation (the "how").
 * Điều này giúp dễ dàng thay thế PayOS bằng cổng thanh toán khác
 * mà không cần thay đổi business logic.
 * </p>
 * <p>
 * Cấu hình PayOS (Client ID, API Key, Checksum Key) được đọc động từ
 * bảng {@code SystemConfigs} để Admin có thể thay đổi mà không cần restart (BR-51).
 * </p>
 */
public interface PaymentGatewayPort {

    /**
     * Tạo link thanh toán PayOS và trả về thông tin QR code + checkout URL.
     *
     * @param orderCode   mã đơn hàng duy nhất (long, dùng để định danh Payment)
     * @param amount      số tiền thanh toán (đơn vị: VND)
     * @param description mô tả giao dịch hiển thị cho người dùng
     * @param returnUrl   URL redirect khi thanh toán thành công
     * @param cancelUrl   URL redirect khi người dùng hủy thanh toán
     * @return thông tin link thanh toán PayOS đã tạo
     */
    PaymentLinkResult createPaymentLink(long orderCode, BigDecimal amount,
                                        String description,
                                        String returnUrl, String cancelUrl);

    /**
     * Hủy link thanh toán đang tồn tại trên PayOS.
     * Thường dùng khi sinh viên hủy đơn hoặc payment timeout.
     *
     * @param paymentLinkId ID của payment link PayOS cần hủy
     */
    void cancelPaymentLink(String paymentLinkId);

    /**
     * Xác minh chữ ký HMAC-SHA256 của webhook gửi từ PayOS bằng chuỗi JSON thô (raw JSON body).
     */
    boolean verifyWebhookChecksum(String rawBody);

    /**
     * Bản dùng DTO.
     */
    boolean verifyWebhookChecksum(PayOSWebhookRequest webhookRequest);

    /**
     * Yêu cầu PayOS thực hiện hoàn tiền cho giao dịch đã thanh toán.
     * Chỉ gọi khi Exam Staff APPROVE đơn phúc khảo và điểm có thay đổi (BR-44).
     *
     * @param paymentLinkId ID của payment link PayOS cần hoàn tiền
     * @param amount        số tiền cần hoàn (thường là toàn bộ 200.000 VND)
     */
    void refundPayment(String paymentLinkId, BigDecimal amount);

    /**
     * Kết quả trả về từ PayOS sau khi tạo link thanh toán thành công.
     *
     * @param paymentLinkId ID link thanh toán PayOS
     * @param checkoutUrl   URL trang thanh toán
     * @param qrCodeUrl     URL ảnh QR code
     * @param status        trạng thái ban đầu của link (thường là "PENDING")
     */
    record PaymentLinkResult(
            String paymentLinkId,
            String checkoutUrl,
            String qrCodeUrl,
            String status
    ) {}
}
