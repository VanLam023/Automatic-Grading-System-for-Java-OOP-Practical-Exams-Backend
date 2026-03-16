package agsfjope.backend.application.dtos.responses.config;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Response DTO representing AI connection test result.
 */
@Data
@Builder
public class TestAiConnectionResponse {

    /**
     * Whether connection test succeeded.
     */
    private Boolean isConnected;

    /**
     * Response latency in milliseconds.
     */
    private Long latencyMs;

    /**
     * Model name returned by provider.
     */
    private String modelName;

    /**
     * Error message when test fails.
     */
    private String errorMessage;

    /**
     * Timestamp when test was executed.
     */
    private OffsetDateTime testedAt;
}
