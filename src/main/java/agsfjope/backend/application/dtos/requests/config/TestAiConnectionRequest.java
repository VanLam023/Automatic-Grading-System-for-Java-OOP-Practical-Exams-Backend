package agsfjope.backend.application.dtos.requests.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO for testing connectivity to AI provider.
 */
@Data
public class TestAiConnectionRequest {

    /**
     * AI provider identifier.
     */
    @NotBlank(message = "Provider không được để trống")
    private String provider;

    /**
     * AI model name to test.
     */
    @NotBlank(message = "Model không được để trống")
    private String model;

    /**
     * AI API key used for test request.
     */
    @NotBlank(message = "API key không được để trống")
    private String apiKey;
}
