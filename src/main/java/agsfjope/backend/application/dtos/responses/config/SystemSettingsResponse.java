package agsfjope.backend.application.dtos.responses.config;

import agsfjope.backend.core.enums.GradingMode;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * Response DTO for generic system settings.
 */
@Data
@Builder
public class SystemSettingsResponse {

    /**
     * Maximum upload size for submission files (MB).
     */
    private Integer maxUploadSizeMb;

    /**
     * Maximum exam paper package size (MB).
     */
    private Integer maxExamPaperMb;

    /**
     * SMTP server host.
     */
    private String smtpHost;

    /**
     * SMTP server port.
     */
    private Integer smtpPort;

    /**
     * Masked SMTP username.
     */
    private String smtpUsername;

    /**
     * Sender email address.
     */
    private String smtpFromEmail;

    /**
     * Current default grading mode.
     */
    private GradingMode defaultGradingMode;

    /**
     * Appeal deadline in days.
     */
    private Integer appealDeadlineDays;

    /**
     * Minimum score to be considered PASS.
     * Score must be GREATER THAN this value to pass.
     * Default = 0 (any score > 0 passes).
     */
    private BigDecimal gradingPassThreshold;
}
