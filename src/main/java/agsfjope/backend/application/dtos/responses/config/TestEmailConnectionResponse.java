package agsfjope.backend.application.dtos.responses.config;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Response DTO representing SMTP email connection test result.
 */
@Data
@Builder
public class TestEmailConnectionResponse {

    /**
     * Whether SMTP connection test succeeded.
     */
    private Boolean isConnected;

    /**
     * Response latency in milliseconds.
     */
    private Long latencyMs;

    /**
     * Error message when test fails.
     */
    private String errorMessage;

    /**
     * Timestamp when test was executed.
     */
    private OffsetDateTime testedAt;
}
