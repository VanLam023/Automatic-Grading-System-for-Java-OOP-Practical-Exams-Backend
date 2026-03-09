package agsfjope.backend.application.dtos.responses.auth;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * DTO representing the user's profile data returned by GET /api/auth/me.
 * Contains the publicly shareable fields of the User entity.
 * Password hash and internal audit fields are intentionally excluded.
 */
@Data
@Builder
public class UserProfileResponse {

    /** The unique identifier of the user. */
    private UUID userId;

    /** The username used for login. */
    private String username;

    /** The user's registered email address. */
    private String email;

    /** The user's full display name. */
    private String fullName;

    /** The name of the role assigned to this user (e.g. ADMIN, STAFF, LECTURER, STUDENT). */
    private String roleName;

    /** Student ID (Mã số sinh viên). Null for non-student roles. */
    private String mssv;

    /** URL of the user's avatar image. May be null if not set. */
    private String avatarUrl;
}
