package agsfjope.backend.infrastructure.repositories.payment;

import agsfjope.backend.core.entities.Payment;
import agsfjope.backend.core.enums.PaymentStatus;
import agsfjope.backend.core.repositories.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation của {@link PaymentRepository} sử dụng Spring Data JPA.
 * <p>
 * Đây là Adapter tầng Infrastructure: thực thi interface của tầng Core
 * bằng JPA cụ thể, giữ cho tầng Application hoàn toàn độc lập với framework.
 * </p>
 */
@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Payment save(Payment payment) {
        return paymentJpaRepository.save(payment);
    }

    @Override
    public Optional<Payment> findByPaymentId(UUID paymentId) {
        return paymentJpaRepository.findById(paymentId);
    }

    @Override
    public Optional<Payment> findByPayosOrderId(String payosOrderId) {
        return paymentJpaRepository.findByPayosOrderId(payosOrderId);
    }

    @Override
    public Optional<Payment> findByAppealId(UUID appealId) {
        return paymentJpaRepository.findByAppealId(appealId);
    }

    @Override
    public List<Payment> findExpiredPendingPayments(OffsetDateTime now) {
        return paymentJpaRepository.findExpiredPendingPayments(now);
    }

    @Override
    public List<Payment> findPendingWalletDepositsByStudentId(UUID studentId) {
        return paymentJpaRepository.findPendingWalletDepositsByStudentId(studentId);
    }

    @Override
    public int markSuccessIfPending(UUID paymentId, OffsetDateTime paidAt, String payosWebhookData) {
        return paymentJpaRepository.markSuccessIfPending(paymentId, paidAt, payosWebhookData, OffsetDateTime.now());
    }

    @Override
    public int markFailedIfPending(UUID paymentId) {
        return paymentJpaRepository.markFailedIfPending(paymentId, OffsetDateTime.now());
    }

    @Override
    public void updateStatus(UUID paymentId, PaymentStatus newStatus) {
        // Gọi custom JPQL UPDATE để tránh phải load toàn bộ entity
        paymentJpaRepository.updateStatus(paymentId, newStatus, OffsetDateTime.now());
    }

    @Override
    public List<Payment> findAllPayments(OffsetDateTime from, OffsetDateTime to, String search) {
        return paymentJpaRepository.findAllPayments(from, to, search);
    }

    @Override
    public Page<Payment> findAllPaymentsPaged(OffsetDateTime from, OffsetDateTime to, String search, Pageable pageable) {
        return paymentJpaRepository.findAllPaymentsPaged(from, to, search, pageable);
    }
}
