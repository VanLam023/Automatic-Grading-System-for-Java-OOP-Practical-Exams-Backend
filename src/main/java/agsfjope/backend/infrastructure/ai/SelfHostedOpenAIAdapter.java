package agsfjope.backend.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Self-hosted OpenAI-compatible API HTTP " + response.statusCode()
                    + ": " + truncate(response.body(), 300));
        }

        return extractText(response.body());
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
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}