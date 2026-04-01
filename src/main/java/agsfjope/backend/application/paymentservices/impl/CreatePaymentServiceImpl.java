package agsfjope.backend.application.paymentservices.impl;

import agsfjope.backend.application.dtos.responses.payment.PaymentResponse;
import agsfjope.backend.application.paymentservices.CreatePaymentService;
import agsfjope.backend.application.ports.out.PaymentGatewayPort;
import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.entities.Payment;
import agsfjope.backend.core.entities.SystemConfig;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.PaymentStatus;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.core.repositories.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation của {@link CreatePaymentService}.
 * <p>
 * Tạo giao dịch thanh toán PayOS cho đơn phúc khảo.
 * Đọc phí ({@code PAYOS_APPEAL_FEE}) và timeout ({@code PAYOS_PAYMENT_TIMEOUT_MINUTES})
 * động từ bảng {@code SystemConfigs} mỗi lần thực hiện (BR-51).
 * </p>
 * <p>
 * Được gọi trong cùng {@code @Transactional} với {@code CreateAppealUseCase}:
 * nếu tạo link PayOS thất bại, toàn bộ transaction Appeal + Payment đều rollback.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreatePaymentServiceImpl implements CreatePaymentService {

    // Config keys trong bảng SystemConfigs
    private static final String KEY_APPEAL_FEE      = "APPEAL_FEE";
    private static final String KEY_TIMEOUT_MINUTES  = "PAYMENT_TIMEOUT_MIN";

    private static final List<String> PAYMENT_CONFIG_KEYS =
            List.of(KEY_APPEAL_FEE, KEY_TIMEOUT_MINUTES);

    /** Giá trị mặc định nếu config chưa được set (BR-32: 200.000 VND). */
    private static final BigDecimal DEFAULT_APPEAL_FEE = new BigDecimal("200000");

    /** Giá trị mặc định nếu config chưa được set (BR-33: 15 phút). */
    private static final int DEFAULT_TIMEOUT_MINUTES = 15;

    private final SystemConfigRepository systemConfigRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayPort paymentGatewayPort;

    /**
     * Tạo giao dịch thanh toán cho đơn phúc khảo.
     * <p>
     * Luồng thực thi:
     * <ol>
     *   <li>Đọc phí và timeout từ SystemConfigs</li>
     *   <li>Sinh {@code orderCode} duy nhất từ timestamp (long)</li>
     *   <li>Gọi PayOS tạo link thanh toán</li>
     *   <li>Lưu Payment vào DB với {@code expiresAt = now + timeoutMinutes}</li>
     *   <li>Trả về PaymentResponse cho client</li>
     * </ol>
     * </p>
     *
     * @param appeal      entity Appeal vừa lưu
     * @param student     sinh viên tạo đơn
     * @param description mô tả giao dịch PayOS
     * @param returnUrl   URL khi thanh toán thành công
     * @param cancelUrl   URL khi hủy thanh toán
     * @return thông tin payment cho client hiển thị QR code
     */
    @Override
    @Transactional
    public PaymentResponse createPayment(Appeal appeal, User student,
                                         String description,
                                         String returnUrl, String cancelUrl) {
        log.info("[Payment] Creating payment for appeal: {}", appeal.getAppealId());

        // Bước 1: Đọc cấu hình từ DB (BR-51 — thay đổi có hiệu lực ngay)
        PaymentConfig config = loadPaymentConfig();

        // Bước 2: Sinh orderCode duy nhất — dùng System.currentTimeMillis()
        // PayOS yêu cầu orderCode là số nguyên dương, tối đa 9007199254740991
        long orderCode = System.currentTimeMillis();

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusMinutes(config.timeoutMinutes());

        // Bước 3: Gọi PayOS tạo link thanh toán
        // Nếu PayOS lỗi → RuntimeException → toàn bộ transaction rollback (Appeal + Payment)
        PaymentGatewayPort.PaymentLinkResult linkResult =
                paymentGatewayPort.createPaymentLink(
                        orderCode,
                        config.appealFee(),
                        description,
                        returnUrl,   // do frontend cung cấp, forward thẳng sang PayOS
                        cancelUrl    // do frontend cung cấp, forward thẳng sang PayOS
                );

        log.info("[Payment] PayOS link created for orderCode: {}", orderCode);

        // Bước 4: Lưu Payment vào DB
        Payment payment = Payment.builder()
                .appeal(appeal)
                .student(student)
                .amount(config.appealFee())
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .payosOrderId(String.valueOf(orderCode))
                .payosPaymentLinkId(linkResult.paymentLinkId())
                .checkoutUrl(linkResult.checkoutUrl())
                .qrCodeUrl(linkResult.qrCodeUrl())
                .expiresAt(expiresAt)
                .build();

        Payment saved = paymentRepository.save(payment);

        log.info("[Payment] Payment record saved: {} | expires: {}",
                saved.getPaymentId(), expiresAt);

        // Bước 5: Trả về DTO cho client
        return PaymentResponse.builder()
                .paymentId(saved.getPaymentId())
                .amount(saved.getAmount())
                .currency(saved.getCurrency())
                .status(saved.getStatus().name())
                .checkoutUrl(saved.getCheckoutUrl())
                .qrCodeUrl(saved.getQrCodeUrl())
                .expiresAt(saved.getExpiresAt())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Đọc phí phúc khảo, timeout và URL redirect từ DB.
     */
    private PaymentConfig loadPaymentConfig() {
        Map<String, SystemConfig> configMap = systemConfigRepository
                .findByConfigKeyIn(PAYMENT_CONFIG_KEYS)
                .stream()
                .collect(Collectors.toMap(SystemConfig::getConfigKey, Function.identity()));

        BigDecimal appealFee = parseDecimalConfig(
                configMap.get(KEY_APPEAL_FEE), DEFAULT_APPEAL_FEE, KEY_APPEAL_FEE);

        int timeoutMinutes = parseIntConfig(
                configMap.get(KEY_TIMEOUT_MINUTES), DEFAULT_TIMEOUT_MINUTES, KEY_TIMEOUT_MINUTES);

        log.debug("[Payment] Config loaded — fee: {} VND | timeout: {} min", appealFee, timeoutMinutes);
        return new PaymentConfig(appealFee, timeoutMinutes);
    }

    /**
     * Parse giá trị config dạng BigDecimal, dùng giá trị mặc định nếu config
     * không tồn tại hoặc không hợp lệ.
     */
    private BigDecimal parseDecimalConfig(SystemConfig config,
                                           BigDecimal defaultValue, String key) {
        if (config == null || config.getConfigValue() == null
                || config.getConfigValue().isBlank()) {
            log.warn("[Payment] Config '{}' not set, using default: {}", key, defaultValue);
            return defaultValue;
        }
        try {
            return new BigDecimal(config.getConfigValue().trim());
        } catch (NumberFormatException e) {
            log.warn("[Payment] Config '{}' has invalid value '{}', using default: {}",
                    key, config.getConfigValue(), defaultValue);
            return defaultValue;
        }
    }

    /**
     * Parse giá trị config dạng int, dùng giá trị mặc định nếu config
     * không tồn tại hoặc không hợp lệ.
     */
    private int parseIntConfig(SystemConfig config, int defaultValue, String key) {
        if (config == null || config.getConfigValue() == null
                || config.getConfigValue().isBlank()) {
            log.warn("[Payment] Config '{}' not set, using default: {}", key, defaultValue);
            return defaultValue;
        }
        try {
            return Integer.parseInt(config.getConfigValue().trim());
        } catch (NumberFormatException e) {
            log.warn("[Payment] Config '{}' has invalid value '{}', using default: {}",
                    key, config.getConfigValue(), defaultValue);
            return defaultValue;
        }
    }

    /**
     * Record nội bộ lưu cấu hình payment.
     */
    private record PaymentConfig(BigDecimal appealFee, int timeoutMinutes) {}
}
