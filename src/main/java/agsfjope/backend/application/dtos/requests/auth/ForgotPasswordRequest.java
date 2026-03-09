package agsfjope.backend.application.dtos.requests.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO representing the request body for the Forgot Password API (Chặng A).
 * Client sends the email address they want to receive the reset link.
 */
@Data
public class ForgotPasswordRequest {

    /** The email address of the account to reset. Must be a valid email format. */
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;
}
