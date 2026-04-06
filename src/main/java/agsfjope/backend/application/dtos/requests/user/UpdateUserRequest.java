package agsfjope.backend.application.dtos.requests.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for Admin to update an existing user account.
 *
 * <p>All fields are optional — only non-null fields will be updated.
 * A null field means "keep the current value".</p>
 *
 * <ul>
 *   <li>{@code roleName} must be one of: STUDENT, EXAM_STAFF, LECTURER, SYSTEM_ADMIN.</li>
 *   <li>If {@code email} is changed, username will be re-derived from the new email.</li>
 *   <li>If {@code username} is provided, it overrides the auto-derived username.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    /** New full display name. Null = keep current. */
    @Size(min = 2, max = 100, message = "FullName phải từ 2 đến 100 ký tự")
    private String fullName;

    /** New email address. Null = keep current. */
    @Email(message = "Email không đúng định dạng")
    private String email;

    /** New username. Null = keep current (or auto-derived from email if email changes). */
    @Size(min = 2, max = 100, message = "Username phải từ 2 đến 100 ký tự")
    private String username;

    /** New student ID (MSSV). Null = keep current. Empty string = clear MSSV. */
    @Size(max = 20, message = "MSSV tối đa 20 ký tự")
    private String mssv;

    /** New phone number. Null = keep current. Empty string = clear phone. */
    @Size(max = 20, message = "Phone tối đa 20 ký tự")
    private String phone;

    /** New role name. Null = keep current. Must be STUDENT | EXAM_STAFF | LECTURER | SYSTEM_ADMIN. */
    private String roleName;
}
