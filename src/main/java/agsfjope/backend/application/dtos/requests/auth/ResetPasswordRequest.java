package agsfjope.backend.application.dtos.requests.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * DTO representing the request body for the Reset Password API (Chặng C).
 * Client sends the token from the URL, the new password, and a confirmation.
 */
@Data
public class ResetPasswordRequest {

    /** The raw reset token from the URL query param (?token=...). */
    @NotBlank(message = "Token không được để trống")
    private String token;

    /**
     * The new password.
     * Must have at least 8 characters, contain an uppercase letter, a digit, and a special character.
     */
    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,}$",
        message = "Mật khẩu phải có ít nhất 8 ký tự, bao gồm chữ in hoa, số và ký tự đặc biệt"
    )
    private String newPassword;

    /** Must exactly match newPassword. */
    @NotBlank(message = "Mật khẩu xác nhận không được để trống")
    private String confirmPassword;

    /**
     * Cross-field validation: newPassword must equal confirmPassword.
     * Bean Validation will call this via @AssertTrue and include it in MethodArgumentNotValidException
     * if the check returns false.
     */
    @AssertTrue(message = "Mật khẩu xác nhận không khớp với mật khẩu mới")
    public boolean isPasswordsMatch() {
        if (newPassword == null || confirmPassword == null) {
            return true; // defer to @NotBlank
        }
        return newPassword.equals(confirmPassword);
    }
}
