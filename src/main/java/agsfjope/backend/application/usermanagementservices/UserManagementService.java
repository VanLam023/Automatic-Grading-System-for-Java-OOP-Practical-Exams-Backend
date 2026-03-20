package agsfjope.backend.application.usermanagementservices;

import agsfjope.backend.application.dtos.requests.user.CreateUserRequest;
import agsfjope.backend.application.dtos.responses.user.CreateUserResponse;
import agsfjope.backend.application.dtos.responses.user.ImportStudentResponse;
import agsfjope.backend.application.dtos.responses.user.UserDetailResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Use Case interface for Admin user management operations.
 * Follows Clean Architecture: the Application layer defines the "what",
 * the implementation class provides the "how".
 *
 * <p>
 * Currently supports bulk import of student accounts from an Excel file.
 * Future operations (delete user, lock/unlock, reset password) can be added
 * here.
 * </p>
 */
public interface UserManagementService {

    /**
     * Parses an uploaded Excel (.xlsx) file containing student data,
     * then bulk-creates Student accounts and sends credential emails.
     *
     * <p>
     * Business rules:
     * <ul>
     * <li>Username is derived by taking the part before '@' in the email.</li>
     * <li>Default password is {@code Abc@123} (BCrypt-hashed before storage).</li>
     * <li>Accounts are created with {@code isActive = false} — the student must
     * change their password on first login to activate.</li>
     * <li>Rows with duplicate email, username, or MSSV are skipped and reported
     * back to the Admin in the response.</li>
     * <li>Credential emails are sent asynchronously so the HTTP response is
     * returned immediately after DB save.</li>
     * </ul>
     * </p>
     *
     * @param file the uploaded .xlsx file; must have columns Email, FullName, MSSV
     * @return summary of the import operation including success/skip counts and
     *         details
     */
    ImportStudentResponse importStudentsFromExcel(MultipartFile file);

    /**
     * Manually creates a single user account.
     * roleName must be STUDENT | EXAM_STAFF | LECTURER.
     * For STUDENT + mssv: email must be @fpt.edu.vn.
     *
     * @param request email, fullName, roleName, optional mssv
     * @return created account details
     * @throws IllegalArgumentException on invalid role, duplicate email/username,
     *                                  or constraint violation
     */
    CreateUserResponse createUser(CreateUserRequest request);

    /**
     * Soft-deletes a user by setting {@code deletedAt = now()}.
     * Cannot delete accounts with role SYSTEM_ADMIN.
     *
     * @param userId target user UUID
     * @throws IllegalArgumentException if user not found, already deleted, or is
     *                                  SYSTEM_ADMIN
     */
    void deleteUser(java.util.UUID userId);

    /**
     * Admin manually activates a user account by UUID.
     * Sets isActive = true, emailVerifiedAt = now (if not already set), isLocked = false.
     *
     * @param userId target user UUID
     * @throws IllegalArgumentException if user not found or already active
     */
    void activateUser(UUID userId);

    /**
     * Returns a paginated list of all non-deleted users.
     *
     * @param pageable pagination / sort config
     * @return page of UserDetailResponse
     */
    Page<UserDetailResponse> getAllUsers(Pageable pageable);

    /**
     * Searches non-deleted users by keyword (username / email / fullName) and/or roleName.
     * Pass null to any parameter to skip that filter.
     *
     * @param keyword  case-insensitive partial match against username, email, fullName
     * @param roleName exact role name filter (e.g. "STUDENT")
     * @param pageable pagination / sort config
     * @return page of matching UserDetailResponse
     */
    Page<UserDetailResponse> searchUsers(String keyword, String roleName, Pageable pageable);

    /**
     * Returns the full detail of a single non-deleted user.
     *
     * @param userId target user UUID
     * @return UserDetailResponse with all admin-visible fields
     * @throws IllegalArgumentException if user not found or soft-deleted
     */
    UserDetailResponse getUserById(UUID userId);
}
