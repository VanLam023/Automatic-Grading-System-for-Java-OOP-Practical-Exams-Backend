package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.responses.payment.PaymentResponse;
import agsfjope.backend.application.paymentservices.HandlePaymentService;
import agsfjope.backend.application.walletservices.WalletService;
import agsfjope.backend.core.entities.Payment;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.PaymentStatus;
import agsfjope.backend.core.repositories.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller xử lý các request liên quan đến thanh toán PayOS.
 * <p>
 * Bao gồm:
 * <ul>
 *   <li>Webhook callback từ PayOS (không yêu cầu JWT — PayOS server gọi vào)</li>
 *   <li>Retry thanh toán khi có lỗi kỹ thuật (yêu cầu JWT của sinh viên)</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final HandlePaymentService handlePaymentService;
    private final PaymentRepository paymentRepository;
    private final WalletService walletService;

    /**
     * Nhận webhook callback từ PayOS sau khi giao dịch hoàn tất.
     * <p>
     * Endpoint này không yêu cầu JWT vì PayOS server gọi trực tiếp vào.
     * Bảo mật được đảm bảo bằng cách verify checksum HMAC-SHA256 bên trong handler (BR-45).
     * </p>
     *
     * @param webhookRequest dữ liệu webhook do PayOS POST vào
     * @return HTTP 200 nếu xử lý thành công, 400 nếu checksum không hợp lệ
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handlePayOSWebhook(
            @RequestBody(required = false) String rawBody) {

        log.info("[PaymentController] Received PayOS webhook");

        if (rawBody == null || rawBody.trim().isEmpty()) {
            log.info("[PaymentController] Empty webhook body — returning 200 for PayOS verification");
            return ResponseEntity.ok(buildResponse(true, "Webhook URL is active", null));
        }

        try {
            // Truyền thẳng raw body string để verify checksum chính xác
            handlePaymentService.handleWebhook(rawBody);
            
            return ResponseEntity.ok(buildResponse(true, "Webhook processed successfully", null));

        } catch (IllegalArgumentException e) {
            log.warn("[PaymentController] Webhook rejected: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(buildResponse(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("[PaymentController] ERROR in Webhook: {}", e.getMessage(), e);
            // Vẫn trả 200 để tránh PayOS retry spam
            return ResponseEntity.ok(buildResponse(true, "Webhook received with internal error log", null));
        }
    }

    /**
     * [DEV ONLY] Simulate thanh toán PayOS thành công cho một payosOrderId.
     * Dùng khi test local mà không có ngrok / PayOS không accessible.
     *
     * <p>Chỉ hoạt động khi payment còn ở trạng thái PENDING.
     * Kích hoạt đúng luồng: creditWallet (nếu WALLET_DEPOSIT) hoặc appeal → PENDING.
     *
     * @param payosOrderId mã orderCode PayOS (lấy từ response /deposit)
     */
    @PostMapping("/dev/simulate-success/{payosOrderId}")
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN', 'ROLE_SYSTEM_ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> simulatePaymentSuccess(
            @PathVariable("payosOrderId") String payosOrderId) {

        log.warn("[PaymentController][DEV] Simulate payment success for orderCode={}", payosOrderId);

        Payment payment = paymentRepository.findByPayosOrderId(payosOrderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy payment với orderCode: " + payosOrderId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Payment đã được xử lý trước đó. Trạng thái hiện tại: " + payment.getStatus());
        }

        log.info("[DEV] PaymentPurpose: {}, Student: {}, DepositFor: {}", 
                payment.getPaymentPurpose(), 
                payment.getStudent() != null ? payment.getStudent().getUserId() : "null",
                payment.getDepositForStudent() != null ? payment.getDepositForStudent().getUserId() : "null");

        // Cập nhật Payment → SUCCESS đúng 1 lần
        int updatedRows = paymentRepository.markSuccessIfPending(
                payment.getPaymentId(),
                java.time.OffsetDateTime.now(),
                "{\"source\":\"dev-simulate-success\"}"
        );
        if (updatedRows == 0) {
            throw new IllegalStateException(
                    "Payment đã được xử lý trước đó. Trạng thái hiện tại: " + payment.getStatus());
        }
        payment.setStatus(PaymentStatus.SUCCESS);

        // Phân nhánh theo mục đích
        if ("WALLET_DEPOSIT".equals(payment.getPaymentPurpose())) {
            // Lấy student nhận tiền (ưu tiên depositForStudent, fallback là student người trả)
            User recipient = payment.getDepositForStudent();
            if (recipient == null) {
                log.warn("[DEV] depositForStudent is null, falling back to student owner");
                recipient = payment.getStudent();
            }

            if (recipient != null) {
                walletService.creditWallet(
                        recipient.getUserId(),
                        payment.getAmount(),
                        payment.getPaymentId());
                log.info("[DEV] Đã cộng {} VND vào ví student {}",
                        payment.getAmount(), recipient.getUserId());
            } else {
                log.error("[DEV] CRITICAL: No recipient found for WALLET_DEPOSIT payment!");
            }
        }

        return ResponseEntity.ok(buildResponse(true,
                String.format("[DEV] Payment %s → SUCCESS. %s VND sử lý xong.",
                        payosOrderId, payment.getAmount()),
                Map.of(
                        "payosOrderId", payosOrderId,
                        "paymentId", payment.getPaymentId(),
                        "amount", payment.getAmount(),
                        "purpose", payment.getPaymentPurpose()
                )));
    }

    /**
     * Retry tạo lại link thanh toán khi giao dịch bị lỗi kỹ thuật.
     * Chỉ cho phép khi Payment còn PENDING và chưa hết hạn.
     *
     * @param appealId UUID của Appeal cần tạo lại link
     * @return thông tin payment mới với QR code và checkout URL
     */
    @PostMapping("/retry/{appealId}")
    public ResponseEntity<Map<String, Object>> retryPayment(
            @PathVariable UUID appealId) {

        log.info("[PaymentController] Retry payment for appeal: {}", appealId);
        PaymentResponse response = handlePaymentService.retryPayment(appealId);

        return ResponseEntity.ok(
                buildResponse(true, "Payment link created successfully", response));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tạo response chuẩn theo format của project:
     * {@code { "success": true/false, "message": "...", "data": {...}, "errors": null }}
     */
    private Map<String, Object> buildResponse(boolean success, String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("data", data);
        response.put("errors", null);
        return response;
    }
}
