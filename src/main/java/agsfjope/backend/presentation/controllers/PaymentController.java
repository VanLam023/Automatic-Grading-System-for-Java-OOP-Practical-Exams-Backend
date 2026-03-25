package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.requests.payment.PayOSWebhookRequest;
import agsfjope.backend.application.dtos.responses.payment.PaymentResponse;
import agsfjope.backend.application.paymentservices.HandlePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
            @RequestBody PayOSWebhookRequest webhookRequest) {

        log.info("[PaymentController] Received PayOS webhook");

        try {
            handlePaymentService.handleWebhook(webhookRequest);
            return ResponseEntity.ok(buildResponse(true, "Webhook processed successfully", null));

        } catch (IllegalArgumentException e) {
            // Checksum không hợp lệ — trả về 400 để PayOS biết
            log.warn("[PaymentController] Webhook rejected: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(buildResponse(false, e.getMessage(), null));
        }
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
