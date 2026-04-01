package agsfjope.backend.application.dtos.requests.config;

import agsfjope.backend.core.enums.GradingMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for updating common system settings.
 */
@Data
public class UpdateSystemSettingsRequest {

    /**
     * Maximum upload size for submission files (MB).
     */
    @NotNull(message = "Max upload size không được để trống")
    @Min(value = 1, message = "Max upload size phải lớn hơn hoặc bằng 1")
    private Integer maxUploadSizeMb;

    /**
     * Maximum exam paper package size (MB).
     */
    @NotNull(message = "Max exam paper size không được để trống")
    @Min(value = 1, message = "Max exam paper size phải lớn hơn hoặc bằng 1")
    private Integer maxExamPaperMb;

    /**
     * SMTP server hostname.
     */
    @NotBlank(message = "SMTP host không được để trống")
    private String smtpHost;

    /**
     * SMTP server port.
     */
    @NotNull(message = "SMTP port không được để trống")
    @Min(value = 1, message = "SMTP port phải lớn hơn hoặc bằng 1")
    @Max(value = 65535, message = "SMTP port không hợp lệ")
    private Integer smtpPort;

    /**
     * SMTP username/account.
     */
    @NotBlank(message = "SMTP username không được để trống")
    private String smtpUsername;

    /**
     * SMTP password. Sensitive value.
     */
    @NotBlank(message = "SMTP password không được để trống")
    private String smtpPassword;

    /**
     * Sender email address used by SMTP service.
     */
    @NotBlank(message = "SMTP from email không được để trống")
    @Email(message = "SMTP from email không đúng định dạng")
    private String smtpFromEmail;

    /**
     * Default grading mode to apply system-wide.
     */
    @NotNull(message = "Default grading mode không được để trống")
    private GradingMode defaultGradingMode;
}
