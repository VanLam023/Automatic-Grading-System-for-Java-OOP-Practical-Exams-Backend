package agsfjope.backend.infrastructure.ai;

/**
 * Strategy interface for LLM (Large Language Model) providers.
 *
 * <p>Each implementation handles the HTTP request format and response parsing
 * for a specific AI provider (Gemini, OpenAI, etc.).</p>
 *
 * <p>Implementations are selected at runtime by {@link LLMAdapterFactory}
 * based on the {@code AI_PROVIDER} value in {@code SystemConfig}.</p>
 */
public interface LLMAdapter {

    /**
     * Sends a single prompt to the LLM and returns the text response.
     *
     * @param prompt   the user message / prompt text
     * @param apiKey   decrypted API key for this provider
     * @param model    model ID (e.g., "gemini-2.0-flash", "gpt-4o")
     * @return text response from the model
     * @throws Exception if the HTTP call fails or the model returns an error
     */
    String chat(String prompt, String apiKey, String model) throws Exception;
}
