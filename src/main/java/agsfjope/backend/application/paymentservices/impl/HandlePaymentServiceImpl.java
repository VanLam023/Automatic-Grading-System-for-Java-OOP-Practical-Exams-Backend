package agsfjope.backend.application.paymentservices.impl;

import agsfjope.backend.application.dtos.requests.payment.PayOSWebhookRequest;
import agsfjope.backend.application.dtos.responses.payment.PaymentResponse;
import agsfjope.backend.application.dtos.responses.payment.AdminPaymentResponse;
import agsfjope.backend.application.paymentservices.HandlePaymentService;
import agsfjope.backend.application.ports.out.PaymentGatewayPort;
import agsfjope.backend.core.entities.Payment;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.enums.PaymentStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implementation của {@link HandlePaymentService}.
 * <p>
 * Điều phối hai luồng chính:
 * <ol>
 *   <li>Webhook từ PayOS: verify checksum nhanh → delegate xử lý bất đồng bộ
 *       sang {@link PaymentWebhookProcessor} (trả HTTP 200 ngay trong ~1ms)</li>
 *   <li>Timeout cleanup: tìm Payment PENDING hết hạn → hủy → cập nhật status</li>
 * </ol>
 * </p>
 * <p>
 * Các chỗ cần tích hợp với Appeal được đánh dấu {@code //TODO: APPEAL_INTEGRATION}.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HandlePaymentServiceImpl implements HandlePaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    /** Xử lý async nội dung webhook sau khi đã verify checksum. */
    private final PaymentWebhookProcessor webhookProcessor;
    private final AppealRepository appealRepository;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────────
    // Webhook Handler
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Entry point webhook từ PayOS.
     * <p>
     * Chỉ làm 2 việc nhanh: (1) verify checksum, (2) kick off async processing.
     * Controller trả HTTP 200 ngay sau khi method này return — đảm bảo PayOS
     * nhận response trong vòng 200ms (skill: Quick Response).
     * </p>
     *
     * @param webhookRequest dữ liệu webhook từ PayOS
     * @throws IllegalArgumentException khi checksum không hợp lệ
     */
    @Override
    public void handleWebhook(String rawBody) {
        log.info("[Payment] Received PayOS raw webhook callback");

        // Verify checksum bằng raw body string (giữ nguyên JSON gốc)
        boolean isValid = paymentGatewayPort.verifyWebhookChecksum(rawBody);
        if (!isValid) {
            log.warn("[Payment] Webhook checksum invalid — skipping processing");
            return;
        }

        // Parse raw body sang DTO để xử lý tiếp
        PayOSWebhookRequest webhookRequest;
        try {
            webhookRequest = objectMapper.readValue(rawBody, PayOSWebhookRequest.class);
        } catch (Exception e) {
            log.error("[Payment] Failed to parse webhook body: {}", e.getMessage());
            return;
        }

        // Check test webhook
        if (isTestWebhook(webhookRequest)) {
            log.info("[Payment] Test/confirmation webhook from PayOS — accepted");
            return;
        }

        // Delegate sang async processor
        webhookProcessor.process(webhookRequest);
    }

    @Override
    public void handleWebhook(PayOSWebhookRequest webhookRequest) {
        // Delegate sang bản String
        try {
            handleWebhook(objectMapper.writeValueAsString(webhookRequest));
        } catch (Exception e) {
            log.error("[Payment] Failed to serialize webhook request: {}", e.getMessage());
        }
    }

    /**
     * Kiểm tra xem đây có phải test webhook PayOS gửi khi verify URL không.
     * Mở rộng điều kiện để cover nhiều trường hợp test của PayOS.
     */
    private boolean isTestWebhook(PayOSWebhookRequest request) {
        if (request == null) return true;
        if (request.getData() == null) return true;
        // PayOS test webhook thường gửi orderCode = 0
        if (request.getData().getOrderCode() == 0) return true;
        // Hoặc code = "00" mà không có orderCode thật
        if ("00".equals(request.getCode()) && request.getData().getOrderCode() <= 0) return true;
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Retry Payment
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tạo lại link thanh toán cho Appeal bị lỗi kỹ thuật.
     * Chỉ cho phép retry khi Payment ở PENDING và chưa hết hạn.
     *
     * @param appealId UUID của Appeal cần retry
     * @return PaymentResponse với QR code và link mới
     */
    @Override
    @Transactional
    public PaymentResponse retryPayment(UUID appealId) {
        Payment payment = paymentRepository.findByAppealId(appealId)
                .orElseThrow(() -> new RuntimeException(
                        "No payment found for appeal: " + appealId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot retry payment with status: " + payment.getStatus());
        }

        if (payment.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalStateException("Payment has already expired. Cannot retry.");
        }

        // Hủy link PayOS cũ nếu còn
        if (payment.getPayosPaymentLinkId() != null) {
            paymentGatewayPort.cancelPaymentLink(payment.getPayosPaymentLinkId());
        }

        // TODO: APPEAL_INTEGRATION — lấy exam name từ Appeal để làm description
        String description = "Phuc khao OOP Exam";

        PaymentGatewayPort.PaymentLinkResult result = paymentGatewayPort.createPaymentLink(
                Long.parseLong(payment.getPayosOrderId()),
                payment.getAmount(),
                description,
                "https://your-app.com/payment/success",  // TODO: đọc từ SystemConfig
                "https://your-app.com/payment/cancel"    // TODO: đọc từ SystemConfig
        );

        payment.setPayosPaymentLinkId(result.paymentLinkId());
        payment.setCheckoutUrl(result.checkoutUrl());
        payment.setQrCodeUrl(result.qrCodeUrl());
        paymentRepository.save(payment);

        log.info("[Payment] Retried payment link for appeal: {}", appealId);
        return buildPaymentResponse(payment);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Expired Payment Cleanup
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Quét và hủy tất cả Payment PENDING đã quá hạn.
     * Gọi bởi {@code PaymentTimeoutScheduler} mỗi 1 phút.
     */
    @Override
    @Transactional
    public void handleExpiredPayments() {
        List<Payment> expiredPayments =
                paymentRepository.findExpiredPendingPayments(OffsetDateTime.now());

        if (expiredPayments.isEmpty()) {
            return;
        }

        log.info("[Payment] Found {} expired PENDING payments to cancel", expiredPayments.size());

        for (Payment payment : expiredPayments) {
            try {
                if (payment.getPayosPaymentLinkId() != null) {
                    paymentGatewayPort.cancelPaymentLink(payment.getPayosPaymentLinkId());
                }

                int updatedRows = paymentRepository.markFailedIfPending(payment.getPaymentId());
                if (updatedRows == 0) {
                    log.info("[Payment] Expired payment {} đã được xử lý trước đó, bỏ qua", payment.getPaymentId());
                    continue;
                }
                log.info("[Payment] Expired payment cancelled: {}", payment.getPaymentId());

                // Cập nhật Appeal status → CANCELLED khi payment hết hạn
                if (payment.getAppeal() != null) {
                    appealRepository.updateStatus(payment.getAppeal().getAppealId(), AppealStatus.CANCELLED);
                    log.info("[Payment] Appeal {} hết hạn thanh toán, chuyển sang CANCELLED",
                            payment.getAppeal().getAppealId());
                }

            } catch (Exception e) {
                log.error("[Payment] Error cancelling expired payment {}: {}",
                        payment.getPaymentId(), e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private PaymentResponse buildPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus().name())
                .qrCodeUrl(payment.getQrCodeUrl())
                .checkoutUrl(payment.getCheckoutUrl())
                .expiresAt(payment.getExpiresAt())
                .build();
    }

    /** Chuyển Payment entity → AdminPaymentResponse DTO. Dùng chung cho cả list và page. */
    private AdminPaymentResponse toAdminPaymentResponse(Payment p) {
        return AdminPaymentResponse.builder()
                .paymentId(p.getPaymentId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus().name())
                .payosOrderId(p.getPayosOrderId())
                .paymentPurpose(p.getPaymentPurpose())
                .expiresAt(p.getExpiresAt())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .studentId(p.getStudent() != null ? p.getStudent().getUserId() : null)
                .studentName(p.getStudent() != null ? p.getStudent().getFullName() : "N/A")
                .studentEmail(p.getStudent() != null ? p.getStudent().getEmail() : "N/A")
                .studentMssv(p.getStudent() != null ? p.getStudent().getMssv() : "N/A")
                .build();
    }

    /** Chuẩn hóa các filter param — đảm bảo không có null truyền vào native query (tránh 42P18). */
    private OffsetDateTime effectiveFrom(OffsetDateTime from) {
        return from != null ? from : OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
    }

    private OffsetDateTime effectiveTo(OffsetDateTime to) {
        return to != null ? to : OffsetDateTime.now().plusYears(100);
    }

    private String effectiveSearch(String search) {
        return (search != null && !search.isBlank()) ? search.trim() : "";
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminPaymentResponse> getAllPaymentsForAdmin(OffsetDateTime from, OffsetDateTime to, String search) {
        log.info("[Payment] Admin fetching transactions. Filter from: {}, to: {}, search: {}", from, to, search);
        return paymentRepository.findAllPayments(effectiveFrom(from), effectiveTo(to), effectiveSearch(search))
                .stream()
                .map(this::toAdminPaymentResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminPaymentResponse> getAllPaymentsForAdminPaged(
            OffsetDateTime from, OffsetDateTime to, String search, Pageable pageable) {
        log.info("[Payment] Admin fetching transactions (paged). page={}, size={}, from={}, to={}, search={}",
                pageable.getPageNumber(), pageable.getPageSize(), from, to, search);
        return paymentRepository.findAllPaymentsPaged(
                        effectiveFrom(from), effectiveTo(to), effectiveSearch(search), pageable)
                .map(this::toAdminPaymentResponse);
    }
}
