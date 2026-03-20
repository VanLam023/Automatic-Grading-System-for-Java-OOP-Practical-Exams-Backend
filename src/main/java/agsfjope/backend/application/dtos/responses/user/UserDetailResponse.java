package agsfjope.backend.application.dtos.responses.user;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO representing a user's full detail for Admin view.
 * Returned by GET /api/admin/users and GET /api/admin/users/{userId}.
 * Password hash and internal security fields are intentionally excluded.
 */
@Data
@Builder
public class UserDetailResponse {

    /** The unique identifier of the user. */
    private UUID userId;

    /** The username used for login. */
    private String username;

    /** The user's registered email address. */
    private String email;

    /** The user's full display name. */
    private String fullName;

    /** The role assigned to this user (e.g. SYSTEM_ADMIN, STUDENT, EXAM_STAFF, LECTURER). */
    private String roleName;

    /** Student ID (Mã số sinh viên). Null for non-student roles. */
    private String mssv;

    /** Phone number. May be null. */
    private String phone;

    /** Avatar URL. May be null. */
    private String avatarUrl;

    /** Whether the account is active (has completed first-login activation). */
    private Boolean isActive;

    /** Whether the account is locked (e.g. too many failed logins). */
    private Boolean isLocked;

    /** Number of consecutive failed login attempts. */
    private Integer loginFailCount;

    /** Timestamp of the user's last successful login. Null if never logged in. */
    private OffsetDateTime lastLoginAt;

    /** Timestamp when the user's email was verified. Null if not yet verified. */
    private OffsetDateTime emailVerifiedAt;

    /** Timestamp when the account was created. */
    private OffsetDateTime createdAt;

    /** Timestamp of the last update to this account. */
    private OffsetDateTime updatedAt;

    /**
     * Timestamp when the account was soft-deleted.
     * Null for active (non-deleted) accounts.
     */
    private OffsetDateTime deletedAt;
}
