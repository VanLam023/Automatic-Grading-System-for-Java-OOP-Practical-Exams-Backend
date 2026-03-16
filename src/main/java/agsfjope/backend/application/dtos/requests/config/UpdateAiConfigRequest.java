package agsfjope.backend.application.dtos.requests.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * DTO for updating AI configuration values.
 */
@Data
public class UpdateAiConfigRequest {

    /**
     * AI provider identifier (e.g., OPENAI, GEMINI).
     */
    @NotBlank(message = "Provider không được để trống")
    private String provider;

    /**
     * AI model name (e.g., gpt-4o-mini, gemini-1.5-pro).
     */
    @NotBlank(message = "Model không được để trống")
    private String model;

    /**
     * API key used to call AI provider. Sensitive value.
     */
    private String apiKey;

    /**
     * Output language for AI comments. Allowed values: vi, en.
     */
    @Pattern(regexp = "^(vi|en)$", message = "Ngôn ngữ chỉ hỗ trợ vi hoặc en")
    private String language;
}
