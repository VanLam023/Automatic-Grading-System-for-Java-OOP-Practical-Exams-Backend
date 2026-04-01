package agsfjope.backend.infrastructure.repositories.payment;

import agsfjope.backend.core.entities.Payment;
import agsfjope.backend.core.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA interface cho entity {@link Payment}.
 * Cung cấp các query cần thiết cho {@code PaymentRepositoryImpl}.
 */
public interface PaymentJpaRepository extends JpaRepository<Payment, UUID> {

    /**
     * Tìm Payment theo mã đơn hàng PayOS (orderCode).
     * Dùng trong webhook handler để ánh xạ callback từ PayOS về giao dịch nội bộ.
     *
     * @param payosOrderId mã orderCode (dạng String)
     * @return Optional chứa Payment nếu tìm thấy
     */
    Optional<Payment> findByPayosOrderId(String payosOrderId);

    /**
     * Tìm Payment theo AppealID.
     *
     * @param appealId UUID của Appeal liên quan
     * @return Optional chứa Payment nếu tìm thấy
     */
    @Query("SELECT p FROM Payment p WHERE p.appeal.appealId = :appealId")
    Optional<Payment> findByAppealId(@Param("appealId") UUID appealId);

    /**
     * Tìm tất cả Payment ở trạng thái PENDING đã quá thời hạn {@code expiresAt}.
     * Dùng bởi {@code PaymentTimeoutScheduler} để tự động hủy (BR-33).
     *
     * @param now thời điểm hiện tại
     * @return danh sách Payment PENDING hết hạn
     */
    @Query("SELECT p FROM Payment p WHERE p.status = 'PENDING' AND p.expiresAt < :now")
    List<Payment> findExpiredPendingPayments(@Param("now") OffsetDateTime now);

    /**
     * Cập nhật trạng thái Payment theo ID.
     *
     * @param paymentId UUID của Payment
     * @param status    trạng thái mới
     */
    @Modifying
    @Transactional
    @Query("UPDATE Payment p SET p.status = :status, p.updatedAt = :now WHERE p.paymentId = :paymentId")
    void updateStatus(@Param("paymentId") UUID paymentId,
                      @Param("status") PaymentStatus status,
                      @Param("now") OffsetDateTime now);
}
