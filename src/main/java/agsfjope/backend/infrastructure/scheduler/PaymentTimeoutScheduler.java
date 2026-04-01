package agsfjope.backend.infrastructure.scheduler;

import agsfjope.backend.application.paymentservices.HandlePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler tự động kiểm tra và hủy các giao dịch thanh toán PENDING đã hết hạn.
 * <p>
 * Chạy mỗi 60 giây (1 phút) để phát hiện các Payment quá hạn timeout 15 phút
 * và tự động hủy chúng (BR-33). Khi làm Appeal, việc hủy Appeal tương ứng
 * cũng sẽ diễn ra bên trong {@code HandlePaymentService.handleExpiredPayments()}.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentTimeoutScheduler {

    private final HandlePaymentService handlePaymentService;

    /**
     * Kiểm tra tất cả Payment PENDING đã quá thời hạn thanh toán mỗi 1 phút.
     * Thời điểm kiểm tra đầu tiên: 60 giây sau khi ứng dụng khởi động.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void checkExpiredPayments() {
        log.debug("[PaymentTimeoutScheduler] Checking for expired PENDING payments...");
        try {
            handlePaymentService.handleExpiredPayments();
        } catch (Exception e) {
            // Ghi log lỗi nhưng không để crash scheduler (BR-46)
            log.error("[PaymentTimeoutScheduler] Error during expired payment cleanup: {}",
                    e.getMessage(), e);
        }
    }
}
