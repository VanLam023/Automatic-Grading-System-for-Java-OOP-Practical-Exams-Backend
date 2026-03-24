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
 * LLM adapter for Anthropic Claude API.
 *
 * <p>Claude uses a different format from OpenAI:</p>
 * <ul>
 *   <li>URL: {@code https://api.anthropic.com/v1/messages}</li>
 *   <li>Auth: {@code x-api-key: {apiKey}} (NOT Bearer)</li>
 *   <li>Required header: {@code anthropic-version: 2023-06-01}</li>
 *   <li>Request body: {@code { "model": "...", "max_tokens": ..., "messages": [...] }}</li>
 *   <li>Response: {@code content[0].text}</li>
 * </ul>
 */
public class ClaudeAdapter implements LLMAdapter {

    private static final String ENDPOINT        = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VER   = "2023-06-01";
    private static final int    TIMEOUT_SECONDS = 60;
    private static final int    MAX_TOKENS      = 2048;

    private final HttpClient   httpClient;
    private final ObjectMapper objectMapper;

    public ClaudeAdapter(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient   = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String chat(String prompt, String apiKey, String model) throws Exception {
        String body = buildRequestBody(prompt, model);

        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("x-api-key", apiKey)                    // Claude auth: x-api-key, NOT Bearer
                .header("anthropic-version", ANTHROPIC_VER)    // Required by Anthropic API
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Claude API HTTP " + response.statusCode()
                    + ": " + truncate(response.body(), 300));
        }

        return extractText(response.body());
    }

    private String buildRequestBody(String prompt, String model) {
        String escaped = escapeJson(prompt);
        return """
                {
                  "model": "%s",
                  "max_tokens": %d,
                  "messages": [
                    {"role": "user", "content": "%s"}
                  ]
                }
                """.formatted(escapeJson(model), MAX_TOKENS, escaped);
    }

    private String extractText(String responseBody) throws Exception {
        JsonNode root    = objectMapper.readTree(responseBody);
        JsonNode content = root.path("content");
        if (content.isEmpty()) {
            throw new RuntimeException("Claude returned empty content. Response: "
                    + truncate(responseBody, 500));
        }
        // content[0].text
        return content.get(0).path("text").asText();
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
