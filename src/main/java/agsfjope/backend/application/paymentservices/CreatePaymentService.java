package agsfjope.backend.application.paymentservices;

import agsfjope.backend.application.dtos.responses.payment.PaymentResponse;
import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.entities.User;

/**
 * Input port interface cho luồng tạo mới thanh toán phúc khảo.
 * <p>
 * Được gọi bởi {@code CreateAppealUseCase} ngay sau khi Appeal được lưu vào DB:
 * <ol>
 *   <li>Đọc phí phúc khảo từ {@code PAYOS_APPEAL_FEE} trong SystemConfigs</li>
 *   <li>Đọc timeout từ {@code PAYOS_PAYMENT_TIMEOUT_MINUTES} trong SystemConfigs</li>
 *   <li>Tạo link thanh toán PayOS</li>
 *   <li>Lưu bản ghi Payment vào DB với {@code expiresAt}</li>
 *   <li>Trả về {@link PaymentResponse} cho client hiển thị QR code</li>
 * </ol>
 * </p>
 * <p>
 * Tách thành interface riêng để {@code CreateAppealUseCase} không phụ thuộc
 * trực tiếp vào implementation PayOS.
 * </p>
 */
public interface CreatePaymentService {

    /**
     * Tạo giao dịch thanh toán PayOS cho đơn phúc khảo.
     * <p>
     * Phí và timeout được đọc động từ {@code SystemConfigs} (BR-51).
     * {@code returnUrl} và {@code cancelUrl} do frontend cung cấp trong API request
     * và được forward thẳng sang PayOS — backend không hardcode hay lưu vào DB.
     * </p>
     * <p>
     * Nếu PayOS trả lỗi, method sẽ throw {@code RuntimeException} để
     * {@code CreateAppealUseCase} có thể rollback toàn bộ transaction.
     * </p>
     *
     * @param appeal      entity Appeal vừa được lưu
     * @param student     sinh viên tạo đơn
     * @param description mô tả giao dịch hiển thị trên PayOS
     * @param returnUrl   URL frontend redirect khi thanh toán thành công
     * @param cancelUrl   URL frontend redirect khi người dùng hủy
     * @return thông tin payment với QR code, checkout URL và thời hạn
     */
    PaymentResponse createPayment(Appeal appeal, User student,
                                  String description,
                                  String returnUrl, String cancelUrl);

}
