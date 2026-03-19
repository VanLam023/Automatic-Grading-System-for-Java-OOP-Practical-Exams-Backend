package agsfjope.backend.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Factory that selects the correct {@link LLMAdapter} based on the provider name
 * stored in {@code SystemConfig.AI_PROVIDER}.
 *
 * <p>Provider resolution rules:</p>
 * <ul>
 *   <li>{@code "gemini"} / {@code "google"} / {@code "google-ai-studio"} → {@link GeminiAdapter}</li>
 *   <li>{@code "openai"} / {@code "chatgpt"} → {@link OpenAIAdapter} (official endpoint)</li>
 *   <li>{@code "openrouter"} → {@link OpenAIAdapter} (OpenRouter endpoint)</li>
 *   <li>{@code "deepseek"} → {@link OpenAIAdapter} (DeepSeek endpoint)</li>
 *   <li>{@code "mistral"} → {@link OpenAIAdapter} (Mistral endpoint)</li>
 *   <li>{@code "xai"} / {@code "grok"} → {@link OpenAIAdapter} (xAI endpoint)</li>
 *   <li>{@code "claude"} / {@code "anthropic"} → {@link ClaudeAdapter} (Anthropic endpoint)</li>
 *   <li>Any URL starting with {@code http://} or {@code https://} → {@link OpenAIAdapter}
 *       with the given URL as endpoint (self-hosted / custom OpenAI-compatible)</li>
 *   <li>Unknown values default to {@link OpenAIAdapter} (OpenAI-compatible)</li>
 * </ul>
 *
 * <p>To add a new provider: just add a case here — no other code changes needed.</p>
 */
@Component
public class LLMAdapterFactory {

    private final HttpClient   httpClient;
    private final ObjectMapper objectMapper;

    public LLMAdapterFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient   = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Returns the appropriate {@link LLMAdapter} for the given provider.
     *
     * @param provider value from {@code AI_PROVIDER} SystemConfig (e.g., "gemini", "openai", or a URL)
     * @return ready-to-use adapter
     */
    public LLMAdapter getAdapter(String provider) {
        if (provider == null || provider.isBlank()) {
            return new GeminiAdapter(httpClient, objectMapper); // default
        }

        String p = provider.trim().toLowerCase();

        // Custom URL — treat as OpenAI-compatible self-hosted
        if (p.startsWith("http://") || p.startsWith("https://")) {
            String endpoint = p.endsWith("/chat/completions") ? p
                    : p.replaceAll("/+$", "") + "/chat/completions";
            return new OpenAIAdapter(endpoint, httpClient, objectMapper);
        }

        return switch (p) {
            case "gemini", "google", "google-ai-studio"
                    -> new GeminiAdapter(httpClient, objectMapper);

            case "openai", "chatgpt"
                    -> new OpenAIAdapter(
                    "https://api.openai.com/v1/chat/completions", httpClient, objectMapper);

            case "openrouter"
                    -> new OpenAIAdapter(
                    "https://openrouter.ai/api/v1/chat/completions", httpClient, objectMapper);

            case "deepseek"
                    -> new OpenAIAdapter(
                    "https://api.deepseek.com/v1/chat/completions", httpClient, objectMapper);

            case "mistral"
                    -> new OpenAIAdapter(
                    "https://api.mistral.ai/v1/chat/completions", httpClient, objectMapper);

            case "xai", "grok"
                    -> new OpenAIAdapter(
                    "https://api.x.ai/v1/chat/completions", httpClient, objectMapper);

            case "claude", "anthropic"
                    -> new ClaudeAdapter(httpClient, objectMapper);

            // Unknown providers: try OpenAI-compatible format
            default -> new OpenAIAdapter(httpClient, objectMapper);
        };
    }
}
