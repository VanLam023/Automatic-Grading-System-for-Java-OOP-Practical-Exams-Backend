package agsfjope.backend.application.dtos.requests.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the data submitted by a student during self-registration.
 * Validation rules enforce FPT email format, password strength, and non-blank fields.
 * The backend will derive and validate {@code username} and {@code mssv} from the email.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    /**
     * Student's FPT institutional email.
     * Must end with @fpt.edu.vn (e.g. lamtvse173173@fpt.edu.vn).
     */
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Pattern(
        regexp = "^[a-zA-Z0-9._%+-]+@fpt\\.edu\\.vn$",
        message = "Email phải là địa chỉ email FPT hợp lệ (dạng @fpt.edu.vn)"
    )
    private String email;

    /**
     * Username chosen by the student.
     * Must match exactly the part before '@' in the FPT email.
     * Example: if email is lamtvse173173@fpt.edu.vn, username MUST be lamtvse173173.
     */
    @NotBlank(message = "Username không được để trống")
    private String username;

    /**
     * Student ID (Mã số sinh viên).
     * Must match the last 8 characters of the username (e.g. se173173 from lamtvse173173).
     */
    @NotBlank(message = "MSSV không được để trống")
    private String mssv;

    /**
     * Student's full name.
     */
    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    /**
     * Account password.
     * Must be at least 8 characters and contain:
     * - At least one uppercase letter (A-Z)
     * - At least one lowercase letter (a-z)
     * - At least one digit (0-9)
     * - At least one special character (@#$%^&+=!)
     */
    @NotBlank(message = "Mật khẩu không được để trống")
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$",
        message = "Mật khẩu phải có ít nhất 8 ký tự, bao gồm chữ hoa, chữ thường, số và ký tự đặc biệt (@#$%^&+=!)"
    )
    private String password;
}
