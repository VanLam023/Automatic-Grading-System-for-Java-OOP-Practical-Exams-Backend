package agsfjope.backend.application.dtos.requests.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for testing SMTP email connection at runtime.
 */
@Data
public class TestEmailConnectionRequest {

    /**
     * SMTP server host.
     */
    @NotBlank(message = "SMTP host không được để trống")
    private String smtpHost;

    /**
     * SMTP server port.
     */
    @NotNull(message = "SMTP port không được để trống")
    @Min(value = 1, message = "SMTP port phải lớn hơn hoặc bằng 1")
    private Integer smtpPort;

    /**
     * SMTP username/account.
     */
    @NotBlank(message = "SMTP username không được để trống")
    private String smtpUsername;

    /**
     * SMTP password.
     */
    @NotBlank(message = "SMTP password không được để trống")
    private String smtpPassword;

    /**
     * Sender email address.
     */
    @NotBlank(message = "SMTP from email không được để trống")
    @Email(message = "SMTP from email không đúng định dạng")
    private String smtpFromEmail;

    /**
     * Target recipient email for test flow.
     */
    @NotBlank(message = "Email nhận test không được để trống")
    @Email(message = "Email nhận test không đúng định dạng")
    private String testToEmail;
}
