package agsfjope.backend.application.paymentservices.impl;

import agsfjope.backend.application.dtos.requests.payment.PayOSWebhookRequest;
import agsfjope.backend.application.ports.out.EmailService;

import agsfjope.backend.core.entities.Payment;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.enums.PaymentStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.payment.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Bean tách biệt để chạy xử lý webhook bất đồng bộ.
 * <p>
 * Phải là Spring Bean riêng (không phải inner method) để {@code @Async}
 * hoạt động đúng thông qua Spring AOP proxy. Nếu gọi nội bộ từ cùng class
 * ({@code this.method()}), AOP sẽ không intercept và {@code @Async} bị bỏ qua.
 * </p>
 * <p>
 * Tuân theo payment-integration skill:
 * <ul>
 *   <li><b>Quick Response</b>: Caller trả HTTP 200 ngay, processor chạy background</li>
 *   <li><b>Idempotency</b>: Kiểm tra trạng thái cuối trước khi xử lý</li>
 *   <li><b>Raw Body Preservation</b>: Lưu webhook data dạng JSON vào DB (audit)</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookProcessor {

    private final PaymentRepository paymentRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final AppealRepository appealRepository;

    /**
     * Xử lý nội dung webhook bất đồng bộ sau khi đã verify checksum.
     * Sử dụng thread pool {@code taskExecutor} từ {@link agsfjope.backend.configuration.AsyncConfig}.
     *
     * @param webhookRequest dữ liệu webhook đã qua verify checksum
     */
    @Async("taskExecutor")
    @Transactional
    public void process(PayOSWebhookRequest webhookRequest) {
        PayOSWebhookRequest.WebhookData data = webhookRequest.getData();
        if (data == null || data.getOrderCode() == null) {
            log.warn("[Payment] Webhook data or orderCode is null");
            return;
        }

        String orderCode = String.valueOf(data.getOrderCode());
        log.info("[Payment] Processing webhook async for orderCode: {}", orderCode);

        // Tìm Payment theo orderCode trong DB
        Payment payment = paymentRepository.findByPayosOrderId(orderCode).orElse(null);
        if (payment == null) {
            log.warn("[Payment] No payment found for orderCode: {}", orderCode);
            return;
        }

        // ── IDEMPOTENCY CHECK ────────────────────────────────────────────────
        // PayOS có thể gửi lại webhook nhiều lần khi hệ thống chậm trả lời.
        // Nếu Payment đã ở trạng thái cuối (SUCCESS/FAILED) từ lần trước,
        // bỏ qua để tránh gửi email trùng hoặc cập nhật Appeal sai (skill: Idempotency).
        if (payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getStatus() == PaymentStatus.FAILED) {
            log.info("[Payment] Duplicate webhook ignored for orderCode: {} (status={})",
                    orderCode, payment.getStatus());
            return;
        }
        // ────────────────────────────────────────────────────────────────────

        // Lưu raw webhook JSON vào DB để audit (BR-45, skill: Raw Body Preservation)
        try {
            String rawJson = objectMapper.writeValueAsString(data);
            payment.setPayosWebhookData(rawJson);
        } catch (JsonProcessingException e) {
            log.warn("[Payment] Could not serialize webhook data for audit: {}", e.getMessage());
        }

        // Xử lý theo mã trạng thái PayOS
        if ("00".equals(webhookRequest.getCode())) {
            onPaymentSuccess(payment, data);
        } else {
            onPaymentFailed(payment);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cập nhật Payment → SUCCESS, lưu thời điểm thanh toán, gửi email xác nhận.
     */
    private void onPaymentSuccess(Payment payment, PayOSWebhookRequest.WebhookData data) {
        log.info("[Payment] SUCCESS for orderCode: {}", data.getOrderCode());

        payment.setPaidAt(java.time.OffsetDateTime.now());
        paymentRepository.updateStatus(payment.getPaymentId(), PaymentStatus.SUCCESS);

        // Lấy examName thực tế từ Appeal → Submission → Block → Exam
        String examName = "OOP Exam";
        try {
            examName = payment.getAppeal().getSubmission().getBlock().getExam().getName();
        } catch (Exception e) {
            log.warn("[Payment] Không thể lấy examName, dùng default");
        }

        // Gửi email xác nhận (TRG-003)
        User student = payment.getStudent();
        if (student != null && student.getEmail() != null) {
            emailService.sendPaymentSuccessEmail(
                    student.getEmail(),
                    student.getFullName(),
                    examName,
                    payment.getAmount().longValue(),
                    String.valueOf(data.getOrderCode())
            );
        }

        // Cập nhật Appeal status → PENDING (sinh viên đã thanh toán, chờ Staff phân công)
        if (payment.getAppeal() != null) {
            appealRepository.updateStatus(payment.getAppeal().getAppealId(), AppealStatus.PENDING);
            log.info("[Payment] Appeal {} chuyển sang PENDING", payment.getAppeal().getAppealId());
        }
    }

    /**
     * Cập nhật Payment → FAILED khi giao dịch bị hủy hoặc thất bại.
     */
    private void onPaymentFailed(Payment payment) {
        log.info("[Payment] FAILED for paymentId: {}", payment.getPaymentId());

        paymentRepository.updateStatus(payment.getPaymentId(), PaymentStatus.FAILED);

        // Cập nhật Appeal status → CANCELLED (thanh toán thất bại)
        if (payment.getAppeal() != null) {
            appealRepository.updateStatus(payment.getAppeal().getAppealId(), AppealStatus.CANCELLED);
            log.info("[Payment] Appeal {} chuyển sang CANCELLED", payment.getAppeal().getAppealId());
        }
    }
}
