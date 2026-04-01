package agsfjope.backend.infrastructure.payment;

import agsfjope.backend.application.dtos.requests.payment.PayOSWebhookRequest;
import agsfjope.backend.application.ports.out.PaymentGatewayPort;
import agsfjope.backend.core.entities.SystemConfig;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.infrastructure.security.AesEncryptionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation của {@link PaymentGatewayPort} tích hợp với PayOS.
 * <p>
 * Theo cùng pattern với {@code SmtpEmailService}: đọc credentials PayOS
 * từ bảng {@code SystemConfigs} mỗi lần gọi API để đảm bảo Admin thay đổi
 * cấu hình có hiệu lực ngay, không cần restart server (BR-51).
 * </p>
 * <p>
 * Xây dựng HTTP request thủ công bằng Java 11+ {@code HttpClient}
 * để tránh phụ thuộc vào SDK bên thứ 3 (dễ cập nhật khi PayOS thay đổi API).
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayOSAdapter implements PaymentGatewayPort {

    // Keys của PayOS trong bảng SystemConfigs
    private static final String KEY_CLIENT_ID    = "PAYOS_CLIENT_ID";
    private static final String KEY_API_KEY      = "PAYOS_API_KEY";
    private static final String KEY_CHECKSUM_KEY = "PAYOS_CHECKSUM_KEY";

    private static final List<String> PAYOS_KEYS =
            List.of(KEY_CLIENT_ID, KEY_API_KEY, KEY_CHECKSUM_KEY);

    // URL base của PayOS API
    private static final String PAYOS_BASE_URL = "https://api-merchant.payos.vn";

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);

    private final SystemConfigRepository systemConfigRepository;
    private final AesEncryptionUtil aesEncryptionUtil;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API Methods
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tạo link thanh toán PayOS và trả về QR code + checkout URL.
     * Đọc credentials từ DB mỗi lần gọi để đảm bảo config fresh (BR-51).
     *
     * @param orderCode   mã đơn hàng duy nhất
     * @param amount      số tiền VND
     * @param description mô tả giao dịch
     * @param returnUrl   URL khi thành công
     * @param cancelUrl   URL khi hủy
     * @return thông tin link thanh toán
     */
    @Override
    public PaymentLinkResult createPaymentLink(long orderCode, BigDecimal amount,
                                               String description,
                                               String returnUrl, String cancelUrl) {
        // Load credentials PayOS từ DB
        PayOSCredentials creds = loadCredentials();

        // Tính chữ ký HMAC để gửi lên PayOS
        String signature = computeCreateSignature(orderCode, amount.longValue(),
                description, cancelUrl, returnUrl, creds.checksumKey());

        // Tạo request body
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("orderCode", orderCode);
        requestBody.put("amount", amount.longValue());
        requestBody.put("description", description);
        requestBody.put("cancelUrl", cancelUrl);
        requestBody.put("returnUrl", returnUrl);
        requestBody.put("signature", signature);

        // Gọi PayOS API
        String responseJson = callPayOSApi(
                "POST",
                "/v2/payment-requests",
                requestBody,
                creds.clientId(),
                creds.apiKey()
        );

        // Parse response
        return parseCreatePaymentResponse(responseJson);
    }

    /**
     * Hủy link thanh toán đang tồn tại trên PayOS.
     * PayOS API: POST /v2/payment-requests/{id}/cancel
     */
    @Override
    public void cancelPaymentLink(String paymentLinkId) {
        PayOSCredentials creds = loadCredentials();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("cancellationReason", "Payment timeout or user cancelled");

        try {
            callPayOSApi(
                    "POST",
                    "/v2/payment-requests/" + paymentLinkId + "/cancel",
                    requestBody,
                    creds.clientId(),
                    creds.apiKey()
            );
            log.info("[PayOS] Cancelled payment link: {}", paymentLinkId);
        } catch (Exception e) {
            log.error("[PayOS] Failed to cancel payment link {}: {}", paymentLinkId, e.getMessage());
        }
    }

    /**
     * Xác minh chữ ký HMAC-SHA256 của webhook gửi từ PayOS.
     * PayOS ký trên data object: sort key alphabetically, nối key=value bằng &.
     * Thử nhiều cách xử lý null/empty để đảm bảo khớp với PayOS.
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean verifyWebhookChecksum(String rawBody) {
        try {
            PayOSCredentials creds = loadCredentials();
            Map<String, Object> body = objectMapper.readValue(rawBody, Map.class);
            String receivedSignature = (String) body.get("signature");
            Map<String, Object> dataMap = (Map<String, Object>) body.get("data");

            if (receivedSignature == null || dataMap == null) {
                log.warn("[PayOS] Webhook missing signature or data — REJECTED");
                return false;
            }

            String key = creds.checksumKey();
            TreeMap<String, Object> sorted = new TreeMap<>(dataMap);

            // Strategy 1: null → empty string ""
            String s1 = sorted.entrySet().stream()
                    .map(e -> e.getKey() + "=" + (e.getValue() != null ? e.getValue() : ""))
                    .collect(Collectors.joining("&"));
            if (computeHmacSha256(s1, key).equalsIgnoreCase(receivedSignature)) {
                log.info("[PayOS] Webhook checksum VERIFIED (strategy: null→empty)");
                return true;
            }

            // Strategy 2: skip null fields entirely
            String s2 = sorted.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("&"));
            if (computeHmacSha256(s2, key).equalsIgnoreCase(receivedSignature)) {
                log.info("[PayOS] Webhook checksum VERIFIED (strategy: skip-null)");
                return true;
            }

            // Strategy 3: null → literal "null"
            String s3 = sorted.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("&"));
            if (computeHmacSha256(s3, key).equalsIgnoreCase(receivedSignature)) {
                log.info("[PayOS] Webhook checksum VERIFIED (strategy: null→'null')");
                return true;
            }

            // Strategy 4: chỉ 3 field core (amount, orderCode, description) — một số doc đề cập
            String s4 = "amount=" + sorted.getOrDefault("amount", "")
                    + "&description=" + sorted.getOrDefault("description", "")
                    + "&orderCode=" + sorted.getOrDefault("orderCode", "");
            if (computeHmacSha256(s4, key).equalsIgnoreCase(receivedSignature)) {
                log.info("[PayOS] Webhook checksum VERIFIED (strategy: 3-core-fields)");
                return true;
            }

            // Không khớp → log chi tiết và REJECT
            log.error("[PayOS] ===== CHECKSUM MISMATCH =====");
            log.error("[PayOS] Received signature : {}", receivedSignature);
            log.error("[PayOS] Strategy1 (null→'') : {}", computeHmacSha256(s1, key));
            log.error("[PayOS] Strategy2 (skip-null): {}", computeHmacSha256(s2, key));
            log.error("[PayOS] Strategy3 (null→null): {}", computeHmacSha256(s3, key));
            log.error("[PayOS] Strategy4 (3-fields) : {}", computeHmacSha256(s4, key));
            log.error("[PayOS] Data string (s1)      : {}", s1);
            log.error("[PayOS] ChecksumKey length    : {}", key.length());
            log.error("[PayOS] ========================");
            return false;

        } catch (Exception e) {
            log.error("[PayOS] Error in verifyWebhookChecksum: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean verifyWebhookChecksum(PayOSWebhookRequest webhookRequest) {
        try {
            return verifyWebhookChecksum(objectMapper.writeValueAsString(webhookRequest));
        } catch (Exception e) {
            log.error("[PayOS] Error serializing webhook request: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Yêu cầu PayOS thực hiện hoàn tiền (refund).
     * Chỉ gọi khi Exam Staff APPROVE đơn phúc khảo có thay đổi điểm (BR-44).
     *
     * @param paymentLinkId ID payment link PayOS
     * @param amount        số tiền cần hoàn
     */
    @Override
    public void refundPayment(String paymentLinkId, BigDecimal amount) {
        // PayOS hiện chưa hỗ trợ API refund tự động —
        // cần hoàn tiền thủ công qua dashboard hoặc chờ PayOS cập nhật API.
        // TODO: Cập nhật khi PayOS cung cấp Refund API chính thức.
        log.warn("[PayOS] Refund API chưa được hỗ trợ tự động. " +
                "PaymentLinkId: {} | Amount: {} VND. Vui lòng hoàn tiền thủ công qua PayOS dashboard.",
                paymentLinkId, amount);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Đọc credentials PayOS từ DB (ClientID, API Key, Checksum Key).
     * Giải mã các field được mã hóa AES (isEncrypted = true).
     */
    private PayOSCredentials loadCredentials() {
        Map<String, SystemConfig> configMap = systemConfigRepository
                .findByConfigKeyIn(PAYOS_KEYS)
                .stream()
                .collect(Collectors.toMap(SystemConfig::getConfigKey, Function.identity()));

        String clientId    = decryptIfNeeded(configMap.get(KEY_CLIENT_ID));
        String apiKey      = decryptIfNeeded(configMap.get(KEY_API_KEY));
        String checksumKey = decryptIfNeeded(configMap.get(KEY_CHECKSUM_KEY));

        return new PayOSCredentials(clientId, apiKey, checksumKey);
    }

    /**
     * Lấy giá trị config theo key, ném RuntimeException nếu không tìm thấy.
     */
    private String getConfig(Map<String, SystemConfig> map, String key) {
        SystemConfig config = map.get(key);
        if (config == null || config.getConfigValue() == null
                || config.getConfigValue().isBlank()) {
            throw new RuntimeException("[PayOS] Config key '" + key
                    + "' not found or empty in SystemConfigs. Please configure via Admin.");
        }
        return config.getConfigValue();
    }

    /**
     * Giải mã AES nếu field được đánh dấu isEncrypted = true.
     */
    private String decryptIfNeeded(SystemConfig config) {
        if (config == null) {
            throw new RuntimeException("[PayOS] PayOS config not found in SystemConfigs.");
        }
        if (Boolean.TRUE.equals(config.getIsEncrypted())) {
            return aesEncryptionUtil.decrypt(config.getConfigValue());
        }
        return config.getConfigValue();
    }

    /**
     * Gọi PayOS REST API và trả về response body dạng String.
     *
     * @param method      HTTP method (GET, POST, DELETE)
     * @param endpoint    đường dẫn API (bắt đầu bằng /)
     * @param requestBody body JSON (null nếu GET)
     * @param clientId    PayOS Client ID
     * @param apiKey      PayOS API Key
     * @return response body JSON string
     */
    private String callPayOSApi(String method, String endpoint,
                                 Map<String, Object> requestBody,
                                 String clientId, String apiKey) {
        try {
            String bodyJson = (requestBody != null)
                    ? objectMapper.writeValueAsString(requestBody)
                    : "";

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(PAYOS_BASE_URL + endpoint))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("x-client-id", clientId)
                    .header("x-api-key", apiKey);

            // Đặt HTTP method và body phù hợp
            HttpRequest request = switch (method.toUpperCase()) {
                case "POST" -> requestBuilder.POST(
                        HttpRequest.BodyPublishers.ofString(bodyJson)).build();
                case "DELETE" -> requestBuilder.method("DELETE",
                        HttpRequest.BodyPublishers.ofString(bodyJson)).build();
                default -> requestBuilder.GET().build();
            };

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(HTTP_TIMEOUT)
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.error("[PayOS] API error {} on {} {}: {}",
                        response.statusCode(), method, endpoint, response.body());
                throw new RuntimeException("[PayOS] API error " + response.statusCode()
                        + ": " + response.body());
            }

            return response.body();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[PayOS] HTTP error calling " + method
                    + " " + endpoint + ": " + e.getMessage(), e);
        }
    }

    /**
     * Parse response JSON từ PayOS createPaymentLink và trả về {@code PaymentLinkResult}.
     */
    @SuppressWarnings("unchecked")
    private PaymentLinkResult parseCreatePaymentResponse(String responseJson) {
        try {
            Map<String, Object> root = objectMapper.readValue(responseJson, Map.class);
            Map<String, Object> data = (Map<String, Object>) root.get("data");

            if (data == null) {
                throw new RuntimeException("[PayOS] No 'data' field in response: " + responseJson);
            }

            return new PaymentLinkResult(
                    (String) data.get("paymentLinkId"),
                    (String) data.get("checkoutUrl"),
                    (String) data.get("qrCode"),
                    (String) data.get("status")
            );

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[PayOS] Failed to parse createPaymentLink response: "
                    + e.getMessage(), e);
        }
    }

    /**
     * Tính HMAC-SHA256 cho tham số tạo payment link theo spec PayOS.
     * Chuỗi data format: amount=XXX&cancelUrl=XXX&description=XXX&orderCode=XXX&returnUrl=XXX
     */
    private String computeCreateSignature(long orderCode, long amount,
                                           String description,
                                           String cancelUrl, String returnUrl,
                                           String checksumKey) {
        // Theo spec PayOS: sort theo key alphabet, nối bằng & (không URL-encode)
        String data = "amount=" + amount
                + "&cancelUrl=" + cancelUrl
                + "&description=" + description
                + "&orderCode=" + orderCode
                + "&returnUrl=" + returnUrl;
        return computeHmacSha256(data, checksumKey);
    }

    /**
     * Tính HMAC-SHA256 và trả về hex string chữ thường.
     */
    private String computeHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // Chuyển byte array sang hex string
            StringBuilder hexBuilder = new StringBuilder();
            for (byte b : hash) {
                hexBuilder.append(String.format("%02x", b));
            }
            return hexBuilder.toString();

        } catch (Exception e) {
            throw new RuntimeException("[PayOS] HMAC-SHA256 computation failed: " + e.getMessage(), e);
        }
    }

    /** Trả về chuỗi rỗng thay vì null để tránh lỗi khi tạo checksum string. */
    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    /**
     * Record lưu credentials PayOS sau khi đọc từ DB.
     * Chỉ sống trong phạm vi một request để giảm memory footprint.
     */
    private record PayOSCredentials(String clientId, String apiKey, String checksumKey) {}
}
