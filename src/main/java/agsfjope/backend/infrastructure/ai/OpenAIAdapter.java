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
 * LLM adapter for OpenAI-compatible APIs.
 *
 * <p>Works with any OpenAI-compatible provider:</p>
 * <ul>
 *   <li>OpenAI / ChatGPT ({@code https://api.openai.com/v1/chat/completions})</li>
 *   <li>OpenRouter ({@code https://openrouter.ai/api/v1/chat/completions})</li>
 *   <li>DeepSeek, Mistral, xAI/Grok, Cohere, and other compatible providers</li>
 *   <li>Self-hosted vLLM / Ollama with OpenAI-compatible endpoint</li>
 * </ul>
 *
 * <p>API format:</p>
 * <ul>
 *   <li>URL: configured endpoint with {@code /chat/completions}</li>
 *   <li>Auth: {@code Authorization: Bearer {apiKey}}</li>
 *   <li>Request body: {@code { "model": "...", "messages": [{"role": "user", "content": "..."}] }}</li>
 *   <li>Response: {@code choices[0].message.content}</li>
 * </ul>
 */
public class OpenAIAdapter implements LLMAdapter {

    private static final int TIMEOUT_SECONDS = 60;

    /** Default OpenAI endpoint. Can be overridden via AI_PROVIDER custom URL. */
    private final String   endpoint;
    private final HttpClient   httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates an adapter for the official OpenAI endpoint.
     */
    public OpenAIAdapter(HttpClient httpClient, ObjectMapper objectMapper) {
        this("https://api.openai.com/v1/chat/completions", httpClient, objectMapper);
    }

    /**
     * Creates an adapter for a custom OpenAI-compatible endpoint.
     *
     * @param endpoint full URL to the chat completions endpoint
     */
    public OpenAIAdapter(String endpoint, HttpClient httpClient, ObjectMapper objectMapper) {
        this.endpoint     = endpoint;
        this.httpClient   = httpClient;
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

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("OpenAI API HTTP " + response.statusCode()
                    + ": " + truncate(response.body(), 300));
        }

        return extractText(response.body());
    }

    private String buildRequestBody(String prompt, String model) {
        String escaped = escapeJson(prompt);
        // max_tokens tăng từ 2048 → 16384 để đủ chỗ cho JSON output dài (nhiều tiêu chí OOP).
        // OpenAI GPT-4o hỗ trợ tối đa 16384 output tokens; các provider khác thường 4096+.
        return """
                {
                  "model": "%s",
                  "messages": [
                    {"role": "user", "content": "%s"}
                  ],
                  "temperature": 0.2,
                  "max_tokens": 16384
                }
                """.formatted(escapeJson(model), escaped);
    }

    private String extractText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (choices.isEmpty()) {
            throw new RuntimeException("OpenAI returned no choices. Response: "
                    + truncate(responseBody, 500));
        }
        JsonNode choice = choices.get(0);

        // [ANTI-TRUNCATION] Kiểm tra finish_reason — nếu là "length" thì JSON bị cắt giữa chừng.
        // Throw exception để callWithRetry() trong LLMReviewService thử lại.
        String finishReason = choice.path("finish_reason").asText("");
        if ("length".equalsIgnoreCase(finishReason)) {
            throw new RuntimeException(
                    "[OPENAI-TRUNCATED] Response cut off at max_tokens limit (16384). "
                    + "Consider shortening source code or reducing analysis length.");
        }

        return choice
                .path("message")
                .path("content")
                .asText();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "").replace("\t", "\\t");
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
