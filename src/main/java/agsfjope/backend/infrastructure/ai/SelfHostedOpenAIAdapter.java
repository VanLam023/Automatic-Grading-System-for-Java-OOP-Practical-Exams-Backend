package agsfjope.backend.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Dedicated adapter for self-hosted OpenAI-compatible providers such as LM Studio.
 *
 * <p>This adapter is intentionally separate from {@link OpenAIAdapter} so local / on-prem
 * providers can use their own timeout and evolve independently without affecting cloud
 * providers like OpenAI, DeepSeek, Grok, or Mistral.</p>
 */
@Slf4j
public class SelfHostedOpenAIAdapter implements LLMAdapter {

    private static final int TIMEOUT_SECONDS = 300;

    private final String endpoint;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SelfHostedOpenAIAdapter(String endpoint, HttpClient httpClient, ObjectMapper objectMapper) {
        this.endpoint = endpoint;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String chat(String prompt, String apiKey, String model) throws Exception {
        String body = buildRequestBody(prompt, model);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .build();

        long startedAt = System.currentTimeMillis();
        log.warn("[SELF-AI-HTTP-START] endpoint={} model={} promptLen={} bodyLen={} timeoutSeconds={}",
                endpoint, model, prompt != null ? prompt.length() : 0, body.length(), TIMEOUT_SECONDS);

        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.error("[SELF-AI-HTTP-EX] endpoint={} model={} durationMs={} exType={} msg={}",
                    endpoint, model, durationMs, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw ex;
        }

        long durationMs = System.currentTimeMillis() - startedAt;
        log.warn("[SELF-AI-HTTP-END] endpoint={} model={} status={} durationMs={} bodyLen={}",
                endpoint, model, response.statusCode(), durationMs,
                response.body() != null ? response.body().length() : 0);

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("[SELF-AI-HTTP-FAIL] endpoint={} model={} status={} body={}",
                    endpoint, model, response.statusCode(), truncate(response.body(), 500));
            throw new RuntimeException("Self-hosted OpenAI-compatible API HTTP " + response.statusCode()
                    + ": " + truncate(response.body(), 300));
        }

        String text = extractText(response.body());
        log.warn("[SELF-AI-HTTP-TEXT] endpoint={} model={} textLen={} preview={}",
                endpoint, model, text != null ? text.length() : 0, truncate(text, 300));
        return text;
    }

    private String buildRequestBody(String prompt, String model) {
        String escapedPrompt = escapeJson(prompt);
        String escapedModel = escapeJson(model);

        return """
                {
                  "model": "%s",
                  "messages": [
                    {"role": "user", "content": "%s"}
                  ],
                  "temperature": 0.2,
                  "max_tokens": 16384
                }
                """.formatted(escapedModel, escapedPrompt);
    }

    private String extractText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");

        if (!choices.isArray() || choices.isEmpty()) {
            throw new RuntimeException("Self-hosted provider returned no choices. Response: "
                    + truncate(responseBody, 500));
        }

        JsonNode choice = choices.get(0);
        String finishReason = choice.path("finish_reason").asText("");

        if ("length".equalsIgnoreCase(finishReason)) {
            throw new RuntimeException(
                    "[SELF_HOSTED-TRUNCATED] Response cut off at max_tokens limit (16384). "
                            + "Consider shortening source code or reducing analysis length.");
        }

        return choice
                .path("message")
                .path("content")
                .asText();
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }

        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r\n", "\n")
                .replace("\r", "")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String normalized = s.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "...";
    }
}
