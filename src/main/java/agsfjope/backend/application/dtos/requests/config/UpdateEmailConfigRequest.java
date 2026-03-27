package agsfjope.backend.application.dtos.requests.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for updating SMTP email server configuration.
 * All fields are required to ensure a valid and complete SMTP setup.
 */
@Data
public class UpdateEmailConfigRequest {

    /**
     * SMTP server hostname (e.g. smtp.gmail.com).
     */
    @NotBlank(message = "SMTP host không được để trống")
    private String smtpHost;

    /**
     * SMTP server port (e.g. 587 for TLS, 465 for SSL).
     */
    @NotNull(message = "SMTP port không được để trống")
    @Min(value = 1, message = "SMTP port phải lớn hơn hoặc bằng 1")
    @Max(value = 65535, message = "SMTP port không hợp lệ")
    private Integer smtpPort;

    /**
     * SMTP account username (typically the sender email).
     */
    @NotBlank(message = "SMTP username không được để trống")
    private String smtpUsername;

    /**
     * SMTP account password or app-specific password.
     */
    @NotBlank(message = "SMTP password không được để trống")
    private String smtpPassword;

    /**
     * Sender email address displayed in outgoing emails.
     */
    @NotBlank(message = "SMTP from email không được để trống")
    @Email(message = "SMTP from email không đúng định dạng")
    private String smtpFromEmail;
}
