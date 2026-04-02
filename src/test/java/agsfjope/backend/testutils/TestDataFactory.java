package agsfjope.backend.testutils;

import agsfjope.backend.core.entities.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Factory class để tạo mock entities dùng chung trong Unit Tests.
 * Mỗi method trả về một entity với dữ liệu hợp lệ mặc định.
 */
public class TestDataFactory {

    // ─── Role ────────────────────────────────────────────────────────────────

    public static Role createStudentRole() {
        Role role = new Role();
        role.setRoleId(1);
        role.setName("STUDENT");
        return role;
    }

    public static Role createStaffRole() {
        Role role = new Role();
        role.setRoleId(2);
        role.setName("STAFF");
        return role;
    }

    public static Role createLecturerRole() {
        Role role = new Role();
        role.setRoleId(3);
        role.setName("LECTURER");
        return role;
    }

    // ─── User ────────────────────────────────────────────────────────────────

    /**
     * User (Student) đã active, không bị khóa.
     * username: "lamtvse173173", email: "lamtvse173173@fpt.edu.vn"
     */
    public static User createActiveStudent() {
        return User.builder()
                .userId(UUID.randomUUID())
                .role(createStudentRole())
                .username("lamtvse173173")
                .email("lamtvse173173@fpt.edu.vn")
                .passwordHash("$2a$10$hashedpassword")
                .fullName("Lam Tran Van")
                .mssv("se173173")
                .isActive(true)
                .isLocked(false)
                .loginFailCount(0)
                .lastLoginAt(OffsetDateTime.now().minusDays(1))
                .build();
    }

    /** User chưa được kích hoạt (isActive = false) */
    public static User createInactiveStudent() {
        User user = createActiveStudent();
        user.setIsActive(false);
        user.setEmailVerifiedAt(null);
        return user;
    }

    /** User đang bị khóa (isLocked = true, lockedUntil = 1 giờ sau) */
    public static User createLockedStudent() {
        User user = createActiveStudent();
        user.setIsLocked(true);
        user.setLockedUntil(OffsetDateTime.now().plusHours(1));
        return user;
    }

    // ─── RefreshToken ─────────────────────────────────────────────────────────

    /** RefreshToken hợp lệ (chưa revoked, chưa expired) */
    public static RefreshToken createValidRefreshToken(User user) {
        return RefreshToken.builder()
                .refreshTokenId(UUID.randomUUID())
                .user(user)
                .tokenHash("valid-refresh-token-" + UUID.randomUUID())
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .isRevoked(false)
                .build();
    }

    /** RefreshToken đã bị revoked */
    public static RefreshToken createRevokedRefreshToken(User user) {
        return RefreshToken.builder()
                .refreshTokenId(UUID.randomUUID())
                .user(user)
                .tokenHash("revoked-token-" + UUID.randomUUID())
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .isRevoked(true)
                .build();
    }

    /** RefreshToken đã expired (hết hạn 1 giờ trước) */
    public static RefreshToken createExpiredRefreshToken(User user) {
        return RefreshToken.builder()
                .refreshTokenId(UUID.randomUUID())
                .user(user)
                .tokenHash("expired-token-" + UUID.randomUUID())
                .expiresAt(OffsetDateTime.now().minusHours(1))
                .isRevoked(false)
                .build();
    }

    // ─── PasswordResetToken ───────────────────────────────────────────────────

    /** PasswordResetToken hợp lệ (chưa dùng, chưa expired) */
    public static PasswordResetToken createValidPasswordResetToken(User user) {
        return PasswordResetToken.builder()
                .passwordResetTokenId(UUID.randomUUID())
                .user(user)
                .tokenHash("valid-reset-token-" + UUID.randomUUID())
                .expiresAt(OffsetDateTime.now().plusMinutes(15))
                .isUsed(false)
                .build();
    }

    /** PasswordResetToken đã dùng (isUsed = true) */
    public static PasswordResetToken createUsedPasswordResetToken(User user) {
        return PasswordResetToken.builder()
                .passwordResetTokenId(UUID.randomUUID())
                .user(user)
                .tokenHash("used-reset-token-" + UUID.randomUUID())
                .expiresAt(OffsetDateTime.now().plusMinutes(15))
                .isUsed(true)
                .build();
    }

    /** PasswordResetToken đã hết hạn */
    public static PasswordResetToken createExpiredPasswordResetToken(User user) {
        return PasswordResetToken.builder()
                .passwordResetTokenId(UUID.randomUUID())
                .user(user)
                .tokenHash("expired-reset-token-" + UUID.randomUUID())
                .expiresAt(OffsetDateTime.now().minusMinutes(5))
                .isUsed(false)
                .build();
    }
}
