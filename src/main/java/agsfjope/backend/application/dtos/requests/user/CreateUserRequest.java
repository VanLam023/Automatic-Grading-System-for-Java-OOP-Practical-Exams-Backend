package agsfjope.backend.application.dtos.requests.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for Admin to manually create a single user account.
 *
 * <ul>
 *   <li>{@code roleName} must be one of: STUDENT, EXAM_STAFF, LECTURER, SYSTEM_ADMIN.</li>
 *   <li>Username is auto-derived from email (part before '@').</li>
 *   <li>For STUDENT: if {@code mssv} is provided, email must be @fpt.edu.vn.</li>
 *   <li>Default password {@code Abc@123}; account starts inactive.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    /** Allowed values: STUDENT, EXAM_STAFF, LECTURER, SYSTEM_ADMIN. */
    @NotBlank(message = "Role không được để trống")
    private String roleName;

    /** Email address of the new user. */
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    /**
     * Full display name. Must not have leading/trailing spaces,
     * multiple consecutive spaces, or be fewer than 2 words.
     */
    @NotBlank(message = "FullName không được để trống")
    @Size(min = 2, max = 100, message = "FullName phải từ 2 đến 100 ký tự")
    @Pattern(
        regexp = "^\\S+(\\s\\S+)+$",
        message = "FullName phải có ít nhất 2 từ, không có khoảng trắng thừa ở đầu/cuối hoặc liên tiếp"
    )
    private String fullName;

    /**
     * Student ID (MSSV). Optional.
     * If provided for STUDENT role, email must end with @fpt.edu.vn.
     * Ignored for EXAM_STAFF and LECTURER roles.
     */
    @Size(max = 20, message = "MSSV tối đa 20 ký tự")
    private String mssv;
}
