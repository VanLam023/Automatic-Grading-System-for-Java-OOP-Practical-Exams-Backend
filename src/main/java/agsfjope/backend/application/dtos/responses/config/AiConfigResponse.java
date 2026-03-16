package agsfjope.backend.application.dtos.responses.config;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for AI configuration data.
 */
@Data
@Builder
public class AiConfigResponse {

    /**
     * AI provider name.
     */
    private String provider;

    /**
     * AI model name.
     */
    private String model;

    /**
     * Masked AI API key (only last 4 characters visible).
     */
    private String apiKeyMasked;

    /**
     * AI output language.
     */
    private String language;
}
