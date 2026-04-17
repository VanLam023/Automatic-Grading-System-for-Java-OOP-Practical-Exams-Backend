package agsfjope.backend.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * LLM adapter for Google Gemini API.
 *
 * <p>API format:</p>
 * <ul>
 *   <li>URL: {@code .../models/{model}:generateContent?key={apiKey}}</li>
 *   <li>Request body: {@code { "contents": [{ "role": "user", "parts": [{"text": "..."}] }] }}</li>
 *   <li>Response: {@code candidates[0].content.parts[0].text}</li>
 * </ul>
 */
public class GeminiAdapter implements LLMAdapter {

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    // [PERF-STEP1] Increased timeout for long analysis prompts
    private static final int TIMEOUT_SECONDS = 120;

    private final HttpClient   httpClient;
    private final ObjectMapper objectMapper;

    public GeminiAdapter(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient   = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String chat(String prompt, String apiKey, String model) throws Exception {
        return doChat(prompt, apiKey, model, false);
    }

    @Override
    public String chatJson(String prompt, String apiKey, String model) throws Exception {
        return doChat(prompt, apiKey, model, true);
    }

    private String doChat(String prompt, String apiKey, String model, boolean responseJson)
            throws Exception {
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String url = BASE_URL.formatted(model, encodedKey);

        String body = buildRequestBody(prompt, responseJson);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Gemini API HTTP " + response.statusCode()
                    + ": " + truncate(response.body(), 300));
        }

        return extractText(response.body());
    }

    private String buildRequestBody(String prompt, boolean responseJson) {
        String escaped = escapeJson(prompt);
        String mimeTypePart = responseJson
                ? ",\n    \"responseMimeType\": \"application/json\""
                : "";
        // [PERF-STEP1] maxOutputTokens tăng từ 16384 → 32768 để tránh JSON bị cắt giữa chừng
        // khi analysis prompt dài (nhiều file .java). gemini-3-flash-preview hỗ trợ tối đa 65536 tokens.
        // LƯU Ý: KHÔNG đặt comment Java bên trong text block vì Java KHÔNG strip comment ra —
        // chúng được nhúng nguyên vào chuỗi JSON → gây lỗi 400 Bad Request từ Gemini API.
        return """
                {
                  "contents": [
                    {
                      "role": "user",
                      "parts": [{"text": "%s"}]
                    }
                  ],
                  "generationConfig": {
                    "temperature": 0.2,
                    "maxOutputTokens": 32768%s
                  }
                }
                """.formatted(escaped, mimeTypePart);
    }

    private String extractText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode candidates = root.path("candidates");
        if (candidates.isEmpty()) {
            throw new RuntimeException("Gemini returned no candidates. Response: "
                    + truncate(responseBody, 500));
        }
        JsonNode candidate = candidates.get(0);

        // [ANTI-TRUNCATION] Kiểm tra finishReason — nếu là MAX_TOKENS thì JSON bị cắt giữa chừng.
        // Throw exception để callWithRetry() trong LLMReviewService nhận ra và thử lại.
        // Không nên parse JSON lỗi vì sẽ gây ra IllegalArgumentException → failure result sai.
        String finishReason = candidate.path("finishReason").asText("");
        if ("MAX_TOKENS".equalsIgnoreCase(finishReason)) {
            throw new RuntimeException(
                    "[GEMINI-TRUNCATED] Response cut off at MAX_TOKENS limit (32768). "
                    + "Consider shortening source code or reducing analysis length.");
        }

        return candidate
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
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
