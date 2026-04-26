package agsfjope.backend.application.configservices.impl;

import agsfjope.backend.application.configservices.SystemConfigService;
import agsfjope.backend.application.dtos.requests.config.TestAiConnectionRequest;
import agsfjope.backend.application.dtos.requests.config.TestEmailConnectionRequest;
import agsfjope.backend.application.dtos.requests.config.UpdateAiConfigRequest;
import agsfjope.backend.application.dtos.requests.config.UpdateEmailConfigRequest;
import agsfjope.backend.application.dtos.requests.config.UpdatePassThresholdRequest;
import agsfjope.backend.application.dtos.requests.config.UpdatePayosConfigRequest;
import agsfjope.backend.application.dtos.requests.config.UpdateSystemSettingsRequest;
import agsfjope.backend.application.dtos.responses.config.AiConfigResponse;
import agsfjope.backend.application.dtos.responses.config.PayosConfigResponse;
import agsfjope.backend.application.dtos.responses.config.SystemSettingsResponse;
import agsfjope.backend.application.dtos.responses.config.TestAiConnectionResponse;
import agsfjope.backend.application.dtos.responses.config.TestEmailConnectionResponse;
import agsfjope.backend.core.entities.SystemConfig;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.exceptions.config.ConfigNotFoundException;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.infrastructure.security.AesEncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import agsfjope.backend.infrastructure.audit.Auditable;
import agsfjope.backend.core.enums.AuditAction;

import java.net.URI;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Implementation of {@link SystemConfigService}.
 * Handles retrieval/update of grouped system config keys and encrypts sensitive values.
 */
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private static final List<String> AI_KEYS = List.of(
            "AI_PROVIDER", "AI_MODEL", "AI_API_KEY", "AI_LANGUAGE"
    );

    private static final List<String> PAYOS_KEYS = List.of(
            "PAYOS_CLIENT_ID", "PAYOS_API_KEY", "PAYOS_CHECKSUM_KEY", "APPEAL_FEE", "PAYMENT_TIMEOUT_MIN"
    );

    private static final List<String> SYSTEM_KEYS = List.of(
            "MAX_UPLOAD_SIZE_MB", "MAX_EXAM_PAPER_MB",
            "SMTP_HOST", "SMTP_PORT", "SMTP_USERNAME", "SMTP_PASSWORD", "SMTP_FROM_EMAIL",
            "DEFAULT_GRADING_MODE", "APPEAL_DEADLINE_DAYS",
            "GRADING_PASS_THRESHOLD"
    );

    private final SystemConfigRepository systemConfigRepository;
    private final UserRepository userRepository;
    private final AesEncryptionUtil aesEncryptionUtil;

    @Override
    public AiConfigResponse getAiConfig() {
        Map<String, SystemConfig> map = getRequiredConfigMap(AI_KEYS);

        String apiKeyPlain = readConfigValue(map.get("AI_API_KEY"));

        return AiConfigResponse.builder()
                .provider(readConfigValue(map.get("AI_PROVIDER")))
                .model(readConfigValue(map.get("AI_MODEL")))
                .apiKeyMasked(aesEncryptionUtil.mask(apiKeyPlain))
                .language(readConfigValue(map.get("AI_LANGUAGE")))
                .build();
    }

    @Override
    @Transactional
    @Auditable(action = AuditAction.UPDATE, entityType = "SYSTEM_CONFIG")
    public void updateAiConfig(UpdateAiConfigRequest request, String updatedByUsername) {
        User updatedBy = getUserOrThrow(updatedByUsername);

        saveConfig("AI_PROVIDER", request.getProvider(), false, updatedBy);
        saveConfig("AI_MODEL", request.getModel(), false, updatedBy);

        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            saveConfig("AI_API_KEY", request.getApiKey(), true, updatedBy);
        }

        if (request.getLanguage() != null && !request.getLanguage().isBlank()) {
            saveConfig("AI_LANGUAGE", request.getLanguage(), false, updatedBy);
        }
    }

    @Override
    public TestAiConnectionResponse testAiConnection(TestAiConnectionRequest request) {
        Instant start = Instant.now();

        try {
            if (isCustomUrlProvider(request.getProvider())) {
                return testCustomProviderConnection(request, start);
            }

            HttpRequest httpRequest = buildProviderHealthCheckRequest(request);
            HttpResponse<String> response = HTTP_CLIENT.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            long latency = Duration.between(start, Instant.now()).toMillis();
            int statusCode = response.statusCode();
            boolean success = statusCode >= 200 && statusCode < 300;

            return TestAiConnectionResponse.builder()
                    .isConnected(success)
                    .latencyMs(latency)
                    .modelName(request.getModel())
                    .errorMessage(success ? null : formatHttpError(statusCode, response.body()))
                    .testedAt(OffsetDateTime.now())
                    .build();
        } catch (Exception ex) {
            long latency = Duration.between(start, Instant.now()).toMillis();
            return TestAiConnectionResponse.builder()
                    .isConnected(false)
                    .latencyMs(latency)
                    .modelName(request.getModel())
                    .errorMessage(buildDetailedErrorMessage(ex))
                    .testedAt(OffsetDateTime.now())
                    .build();
        }
    }

    private TestAiConnectionResponse testCustomProviderConnection(TestAiConnectionRequest request, Instant start) {
        String endpoint = normalizeCustomModelsEndpoint(request.getProvider());

        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "AGSFJOPE-TestConnection/1.0");

            String apiKey = request.getApiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            }

            int statusCode = connection.getResponseCode();
            String responseBody = readResponseBody(connection, statusCode);
            long latency = Duration.between(start, Instant.now()).toMillis();
            boolean success = statusCode >= 200 && statusCode < 300;

            return TestAiConnectionResponse.builder()
                    .isConnected(success)
                    .latencyMs(latency)
                    .modelName(request.getModel())
                    .errorMessage(success ? null : formatHttpError(statusCode, responseBody))
                    .testedAt(OffsetDateTime.now())
                    .build();
        } catch (SocketTimeoutException ex) {
            long latency = Duration.between(start, Instant.now()).toMillis();
            return TestAiConnectionResponse.builder()
                    .isConnected(false)
                    .latencyMs(latency)
                    .modelName(request.getModel())
                    .errorMessage("Timeout khi gọi endpoint: " + endpoint + " - " + ex.getMessage())
                    .testedAt(OffsetDateTime.now())
                    .build();
        } catch (Exception ex) {
            long latency = Duration.between(start, Instant.now()).toMillis();
            return TestAiConnectionResponse.builder()
                    .isConnected(false)
                    .latencyMs(latency)
                    .modelName(request.getModel())
                    .errorMessage("Lỗi khi gọi endpoint: " + endpoint + " - " + buildDetailedErrorMessage(ex))
                    .testedAt(OffsetDateTime.now())
                    .build();
        }
    }

    private boolean isCustomUrlProvider(String provider) {
        if (provider == null) {
            return false;
        }
        String value = provider.trim().toLowerCase();
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private String normalizeCustomModelsEndpoint(String rawProvider) {
        String normalizedProvider = rawProvider == null ? "" : rawProvider.trim().replaceAll("/+$", "");

        if (normalizedProvider.endsWith("/models")) {
            return normalizedProvider;
        }
        if (normalizedProvider.endsWith("/chat/completions")) {
            return normalizedProvider.replaceFirst("/chat/completions$", "/models");
        }
        return normalizedProvider + "/models";
    }

    private String readResponseBody(HttpURLConnection connection, int statusCode) throws Exception {
        InputStream stream = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();

        if (stream == null) {
            return "";
        }

        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String buildDetailedErrorMessage(Exception ex) {
        String className = ex.getClass().getSimpleName();
        String message = ex.getMessage() == null ? "Không có thông điệp lỗi" : ex.getMessage();
        return className + ": " + message;
    }

    @Override
    public TestEmailConnectionResponse testEmailConnection(TestEmailConnectionRequest request) {
        Instant start = Instant.now();

        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", request.getSmtpHost());
            props.put("mail.smtp.port", String.valueOf(request.getSmtpPort()));

            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "5000");
            props.put("mail.smtp.writetimeout", "5000");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(request.getSmtpUsername(), request.getSmtpPassword());
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(request.getSmtpFromEmail()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(request.getTestToEmail(), false));
            message.setSubject("[AGSFJOPE] SMTP Test Connection", StandardCharsets.UTF_8.name());
            message.setText(
                    "Đây là email kiểm tra kết nối SMTP từ hệ thống AGSFJOPE.\n\n"
                            + "Nếu bạn nhận được email này, cấu hình SMTP đang hoạt động bình thường.",
                    StandardCharsets.UTF_8.name()
            );
            message.setSentDate(java.util.Date.from(Instant.now()));

            try (Transport transport = session.getTransport("smtp")) {
                transport.connect(request.getSmtpHost(), request.getSmtpUsername(), request.getSmtpPassword());
                transport.sendMessage(message, message.getAllRecipients());
            }

            long latency = Duration.between(start, Instant.now()).toMillis();
            return TestEmailConnectionResponse.builder()
                    .isConnected(true)
                    .latencyMs(latency)
                    .errorMessage(null)
                    .testedAt(OffsetDateTime.now())
                    .build();
        } catch (Exception ex) {
            long latency = Duration.between(start, Instant.now()).toMillis();
            return TestEmailConnectionResponse.builder()
                    .isConnected(false)
                    .latencyMs(latency)
                    .errorMessage(ex.getMessage())
                    .testedAt(OffsetDateTime.now())
                    .build();
        }
    }

    private HttpRequest buildProviderHealthCheckRequest(TestAiConnectionRequest request) {
        String rawProvider = request.getProvider() == null ? "" : request.getProvider().trim();
        String provider = normalize(request.getProvider());
        String model = request.getModel();
        String apiKey = request.getApiKey();

        // Hỗ trợ endpoint tùy chỉnh theo chuẩn OpenAI-compatible:
        // provider = "https://your-ai-host/v1", "https://your-ai-host/v1/models"
        // hoặc "https://your-ai-host/v1/chat/completions"
        if (rawProvider.startsWith("http://") || rawProvider.startsWith("https://")) {
            String normalizedProvider = rawProvider.replaceAll("/+$", "");
            String endpoint;
            if (normalizedProvider.endsWith("/models")) {
                endpoint = normalizedProvider;
            } else if (normalizedProvider.endsWith("/chat/completions")) {
                endpoint = normalizedProvider.replaceFirst("/chat/completions$", "/models");
            } else {
                endpoint = normalizedProvider + "/models";
            }

            return HttpRequest.newBuilder(URI.create(endpoint))
                    .GET()
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .build();
        }

        return switch (provider) {
            case "openai" -> buildBearerGetModelsRequest("https://api.openai.com/v1/models", apiKey);
            case "openrouter" -> buildBearerGetModelsRequest("https://openrouter.ai/api/v1/models", apiKey);
            case "mistral" -> buildBearerGetModelsRequest("https://api.mistral.ai/v1/models", apiKey);
            case "deepseek" -> buildBearerGetModelsRequest("https://api.deepseek.com/v1/models", apiKey);
            case "xai", "grok" -> buildBearerGetModelsRequest("https://api.x.ai/v1/models", apiKey);
            case "cohere" -> buildBearerGetModelsRequest("https://api.cohere.com/v1/models", apiKey);
            case "gemini", "google", "google-ai-studio" -> buildGeminiModelsRequest(apiKey);
            case "anthropic", "claude" -> buildAnthropicMessageRequest(model, apiKey);
            default -> throw new IllegalArgumentException(
                    "Provider chưa được hỗ trợ trực tiếp: " + request.getProvider()
                            + ". Hãy truyền provider là URL endpoint chuẩn OpenAI-compatible để test generic."
            );
        };
    }

    private HttpRequest buildBearerGetModelsRequest(String endpoint, String apiKey) {
        return HttpRequest.newBuilder(URI.create(endpoint))
                .GET()
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .build();
    }

    private HttpRequest buildGeminiModelsRequest(String apiKey) {
        String encoded = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models?key=" + encoded;
        return HttpRequest.newBuilder(URI.create(endpoint))
                .GET()
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .build();
    }

    private HttpRequest buildAnthropicMessageRequest(String model, String apiKey) {
        String body = """
                {
                  "model": "%s",
                  "max_tokens": 1,
                  "messages": [
                    {"role": "user", "content": "ping"}
                  ]
                }
                """.formatted(escapeJson(model));

        return HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(20))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .header("accept", "application/json")
                .build();
    }

    private String formatHttpError(int statusCode, String body) {
        String safeBody = body == null ? "" : body.trim();
        if (safeBody.length() > 300) {
            safeBody = safeBody.substring(0, 300) + "...";
        }
        return "HTTP " + statusCode + (safeBody.isEmpty() ? "" : " - " + safeBody);
    }

    private String normalize(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase();
    }

    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    @Override
    public PayosConfigResponse getPayosConfig() {
        Map<String, SystemConfig> map = getRequiredConfigMap(PAYOS_KEYS);

        String clientId = readConfigValue(map.get("PAYOS_CLIENT_ID"));
        String apiKey = readConfigValue(map.get("PAYOS_API_KEY"));
        String checksum = readConfigValue(map.get("PAYOS_CHECKSUM_KEY"));

        return PayosConfigResponse.builder()
                .clientIdMasked(aesEncryptionUtil.mask(clientId))
                .apiKeyMasked(aesEncryptionUtil.mask(apiKey))
                .checksumKeyMasked(aesEncryptionUtil.mask(checksum))
                .appealFee(toBigDecimal(readConfigValue(map.get("APPEAL_FEE"))))
                .paymentTimeoutMin(toInteger(readConfigValue(map.get("PAYMENT_TIMEOUT_MIN"))))
                .build();
    }

    @Override
    @Transactional
    @Auditable(action = AuditAction.UPDATE, entityType = "SYSTEM_CONFIG")
    public void updatePayosConfig(UpdatePayosConfigRequest request, String updatedByUsername) {
        User updatedBy = getUserOrThrow(updatedByUsername);

        saveConfig("PAYOS_CLIENT_ID", request.getClientId(), true, updatedBy);
        saveConfig("PAYOS_API_KEY", request.getApiKey(), true, updatedBy);
        saveConfig("PAYOS_CHECKSUM_KEY", request.getChecksumKey(), true, updatedBy);
        saveConfig("APPEAL_FEE", request.getAppealFee().toPlainString(), false, updatedBy);
        saveConfig("PAYMENT_TIMEOUT_MIN", String.valueOf(request.getPaymentTimeoutMin()), false, updatedBy);
    }

    @Override
    public SystemSettingsResponse getSystemSettings() {
        Map<String, SystemConfig> map = getRequiredConfigMap(SYSTEM_KEYS);

        return SystemSettingsResponse.builder()
                .maxUploadSizeMb(toInteger(readConfigValue(map.get("MAX_UPLOAD_SIZE_MB"))))
                .maxExamPaperMb(toInteger(readConfigValue(map.get("MAX_EXAM_PAPER_MB"))))
                .smtpHost(readConfigValue(map.get("SMTP_HOST")))
                .smtpPort(toInteger(readConfigValue(map.get("SMTP_PORT"))))
                .smtpUsername(readConfigValue(map.get("SMTP_USERNAME")))
                .smtpFromEmail(readConfigValue(map.get("SMTP_FROM_EMAIL")))
                .defaultGradingMode(agsfjope.backend.core.enums.GradingMode.valueOf(readConfigValue(map.get("DEFAULT_GRADING_MODE"))))
                .appealDeadlineDays(toInteger(readConfigValue(map.get("APPEAL_DEADLINE_DAYS"))))
                .gradingPassThreshold(toBigDecimal(readConfigValue(map.get("GRADING_PASS_THRESHOLD"))))
                .build();
    }

    @Override
    @Transactional
    @Auditable(action = AuditAction.UPDATE, entityType = "SYSTEM_CONFIG")
    public void updateSystemSettings(UpdateSystemSettingsRequest request, String updatedByUsername) {
        User updatedBy = getUserOrThrow(updatedByUsername);

        // Update general settings: upload limits and default grading mode
        saveConfig("MAX_UPLOAD_SIZE_MB", String.valueOf(request.getMaxUploadSizeMb()), false, updatedBy);
        saveConfig("MAX_EXAM_PAPER_MB", String.valueOf(request.getMaxExamPaperMb()), false, updatedBy);
        saveConfig("DEFAULT_GRADING_MODE", request.getDefaultGradingMode().name(), false, updatedBy);
    }

    @Override
    @Transactional
    @Auditable(action = AuditAction.UPDATE, entityType = "SYSTEM_CONFIG")
    public void updateEmailConfig(UpdateEmailConfigRequest request, String updatedByUsername) {
        User updatedBy = getUserOrThrow(updatedByUsername);

        // Save SMTP settings; username and password are encrypted at rest
        saveConfig("SMTP_HOST", request.getSmtpHost(), false, updatedBy);
        saveConfig("SMTP_PORT", String.valueOf(request.getSmtpPort()), false, updatedBy);
        saveConfig("SMTP_USERNAME", request.getSmtpUsername(), true, updatedBy);
        saveConfig("SMTP_PASSWORD", request.getSmtpPassword(), true, updatedBy);
        saveConfig("SMTP_FROM_EMAIL", request.getSmtpFromEmail(), false, updatedBy);
    }

    @Override
    @Transactional
    @Auditable(action = AuditAction.UPDATE, entityType = "SYSTEM_CONFIG")
    public void updatePassThreshold(UpdatePassThresholdRequest request, String updatedByUsername) {
        User updatedBy = getUserOrThrow(updatedByUsername);
        saveConfig("GRADING_PASS_THRESHOLD", request.getPassThreshold().toPlainString(), false, updatedBy);
    }

    private Map<String, SystemConfig> getRequiredConfigMap(List<String> keys) {
        List<SystemConfig> configs = systemConfigRepository.findByConfigKeyIn(keys);
        Map<String, SystemConfig> map = configs.stream()
                .collect(Collectors.toMap(SystemConfig::getConfigKey, Function.identity()));

        for (String key : keys) {
            if (!map.containsKey(key)) {
                throw new ConfigNotFoundException("Không tìm thấy cấu hình hệ thống cho key: " + key);
            }
        }

        return map;
    }

    private String readConfigValue(SystemConfig config) {
        String raw = config.getConfigValue();
        if (Boolean.TRUE.equals(config.getIsEncrypted())) {
            return aesEncryptionUtil.decrypt(raw);
        }
        return raw;
    }

    private void saveConfig(String key, String value, boolean encrypted, User updatedBy) {
        SystemConfig config = systemConfigRepository.findByConfigKey(key)
                .orElseThrow(() -> new ConfigNotFoundException("Không tìm thấy cấu hình hệ thống cho key: " + key));

        String storedValue = encrypted ? aesEncryptionUtil.encrypt(value) : value;
        config.setConfigValue(storedValue);
        config.setIsEncrypted(encrypted);
        config.setUpdatedBy(updatedBy);
        systemConfigRepository.save(config);
    }

    private User getUserOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ConfigNotFoundException("Không tìm thấy người dùng cập nhật: " + username));
    }

    private Integer toInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể parse Integer từ config value: " + value, ex);
        }
    }

    private BigDecimal toBigDecimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể parse BigDecimal từ config value: " + value, ex);
        }
    }
}
