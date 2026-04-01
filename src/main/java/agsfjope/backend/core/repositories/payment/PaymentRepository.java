package agsfjope.backend.core.repositories.payment;

import agsfjope.backend.core.entities.Payment;
import agsfjope.backend.core.enums.PaymentStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface cho entity {@link Payment}.
 * <p>
 * Nằm ở tầng Core (Domain) theo Clean Architecture. Interface này
 * định nghĩa các operation cần thiết mà tầng Application sẽ dùng.
 * Tầng Infrastructure ({@code PaymentRepositoryImpl}) sẽ implement
 * bằng Spring Data JPA.
 * </p>
 */
public interface PaymentRepository {

    /**
     * Lưu hoặc cập nhật một bản ghi Payment vào database.
     *
     * @param payment entity Payment cần lưu
     * @return entity Payment đã được lưu (có ID nếu là mới)
     */
    Payment save(Payment payment);

    /**
     * Tìm kiếm Payment theo UUID nội bộ.
     *
     * @param paymentId UUID của Payment
     * @return Optional chứa Payment nếu tìm thấy
     */
    Optional<Payment> findByPaymentId(UUID paymentId);

    /**
     * Tìm kiếm Payment theo mã đơn hàng PayOS.
     * Dùng trong webhook handler để tìm giao dịch tương ứng (BR-42).
     *
     * @param payosOrderId mã orderCode do hệ thống sinh ra khi tạo link
     * @return Optional chứa Payment nếu tìm thấy
     */
    Optional<Payment> findByPayosOrderId(String payosOrderId);

    /**
     * Tìm kiếm Payment theo ID của Appeal liên quan.
     *
     * @param appealId UUID của Appeal
     * @return Optional chứa Payment nếu tìm thấy
     */
    Optional<Payment> findByAppealId(UUID appealId);

    /**
     * Tìm tất cả Payment ở trạng thái PENDING đã quá thời hạn thanh toán.
     * <p>
     * Dùng bởi {@code PaymentTimeoutScheduler} để tự động hủy các giao dịch
     * hết hạn (BR-33: timeout 15 phút).
     * </p>
     *
     * @param now thời điểm hiện tại để so sánh với {@code expiresAt}
     * @return danh sách Payment PENDING đã quá hạn
     */
    List<Payment> findExpiredPendingPayments(OffsetDateTime now);

    /**
     * Cập nhật trạng thái của Payment.
     *
     * @param paymentId UUID của Payment cần cập nhật
     * @param newStatus trạng thái mới (SUCCESS / FAILED / REFUNDED)
     */
    void updateStatus(UUID paymentId, PaymentStatus newStatus);
}
