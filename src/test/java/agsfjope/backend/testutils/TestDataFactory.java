package agsfjope.backend.testutils;

import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.enums.GradingMode;
import agsfjope.backend.core.enums.ExamStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
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

    // ─── SystemConfig ──────────────────────────────────────────────────────────

    /** Tạo 1 SystemConfig với giá trị plain text (isEncrypted = false) */
    public static SystemConfig createPlainConfig(String key, String value) {
        return SystemConfig.builder()
                .systemConfigId(1)
                .configKey(key)
                .configValue(value)
                .isEncrypted(false)
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    /** Tạo 1 SystemConfig với giá trị đã mã hóa (isEncrypted = true) */
    public static SystemConfig createEncryptedConfig(String key, String encryptedValue) {
        return SystemConfig.builder()
                .systemConfigId(2)
                .configKey(key)
                .configValue(encryptedValue)
                .isEncrypted(true)
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    /** Tạo danh sách configs cho AI group (AI_PROVIDER, AI_MODEL, AI_API_KEY, AI_LANGUAGE) */
    public static java.util.List<SystemConfig> createAiConfigList() {
        return java.util.List.of(
                createPlainConfig("AI_PROVIDER", "gemini"),
                createPlainConfig("AI_MODEL", "gemini-1.5-pro"),
                createEncryptedConfig("AI_API_KEY", "ENCRYPTED_AI_KEY"),
                createPlainConfig("AI_LANGUAGE", "Vietnamese")
        );
    }

    /** Tạo danh sách configs cho PayOS group */
    public static java.util.List<SystemConfig> createPayosConfigList() {
        return java.util.List.of(
                createEncryptedConfig("PAYOS_CLIENT_ID", "ENCRYPTED_CLIENT_ID"),
                createEncryptedConfig("PAYOS_API_KEY", "ENCRYPTED_PAYOS_KEY"),
                createEncryptedConfig("PAYOS_CHECKSUM_KEY", "ENCRYPTED_CHECKSUM"),
                createPlainConfig("APPEAL_FEE", "50000"),
                createPlainConfig("PAYMENT_TIMEOUT_MIN", "15")
        );
    }

    /** Tạo danh sách configs cho System Settings group */
    public static java.util.List<SystemConfig> createSystemSettingsConfigList() {
        return java.util.List.of(
                createPlainConfig("MAX_UPLOAD_SIZE_MB", "50"),
                createPlainConfig("MAX_EXAM_PAPER_MB", "10"),
                createPlainConfig("SMTP_HOST", "smtp.gmail.com"),
                createPlainConfig("SMTP_PORT", "587"),
                createPlainConfig("SMTP_USERNAME", "test@gmail.com"),
                createPlainConfig("SMTP_PASSWORD", "secret"),
                createPlainConfig("SMTP_FROM_EMAIL", "noreply@fpt.edu.vn"),
                createPlainConfig("DEFAULT_GRADING_MODE", "MODE_1"),
                createPlainConfig("APPEAL_DEADLINE_DAYS", "7")
        );
    }

    // ─── Exam ────────────────────────────────────────────────────────────────

    /**
     * Exam đang ONGOING: StartTime = 1 giờ trước, EndTime = 1 giờ sau.
     * semester: "FA", academicYear: "2025-2026"
     */
    public static Exam createOngoingExam() {
        return Exam.builder()
                .examId(UUID.randomUUID())
                .name("PRO192 Practical Exam")
                .semester("FA")
                .academicYear("2026")
                .status(ExamStatus.ONGOING)
                .startTime(OffsetDateTime.now().minusHours(1))
                .endTime(OffsetDateTime.now().plusHours(2))
                .gradingMode(GradingMode.MODE_1)
                .build();
    }

    // ─── Block ────────────────────────────────────────────────────────────────

    /**
     * Block đang ONGOING: StartTime = 1 giờ trước, EndTime = 1 giờ sau.
     * name: "Block 10"
     */
    public static Block createOngoingBlock(Exam exam) {
        return Block.builder()
                .blockId(UUID.randomUUID())
                .exam(exam)
                .name("Block 10")
                .examDate(LocalDate.now())
                .startTime(OffsetDateTime.now().minusHours(1))
                .endTime(OffsetDateTime.now().plusHours(1))
                .build();
    }

    /** Block chưa bắt đầu: StartTime = 2 giờ sau. */
    public static Block createNotStartedBlock(Exam exam) {
        return Block.builder()
                .blockId(UUID.randomUUID())
                .exam(exam)
                .name("Block 10")
                .startTime(OffsetDateTime.now().plusHours(2))
                .endTime(OffsetDateTime.now().plusHours(4))
                .build();
    }

    /** Block đã kết thúc: EndTime = 2 giờ trước. */
    public static Block createFinishedBlock(Exam exam) {
        return Block.builder()
                .blockId(UUID.randomUUID())
                .exam(exam)
                .name("Block 10")
                .startTime(OffsetDateTime.now().minusHours(4))
                .endTime(OffsetDateTime.now().minusHours(2))
                .build();
    }

    // ─── Submission ───────────────────────────────────────────────────────────

    public static Submission createSubmission(User student, Block block) {
        return Submission.builder()
                .submissionId(UUID.randomUUID())
                .student(student)
                .block(block)
                .fileName("MySolution.zip")
                .filePath("submissions/Fall-2025/Block 10/Lam Tran Van - se173173/MySolution.zip")
                .fileSizeBytes(1024L * 512)  // 512 KB
                .build();
    }

    // ─── Question ─────────────────────────────────────────────────────────────

    public static Question createQuestion(int number) {
        return Question.builder()
                .questionId(UUID.randomUUID())
                .questionNumber(number)
                .title("Question " + number)
                .build();
    }

    // ─── AuditLog ─────────────────────────────────────────────────────────────

    /**
     * AuditLog do user thực hiện hành động LOGIN.
     * username: "lamtvse173173"
     */
    public static agsfjope.backend.core.entities.AuditLog createAuditLogWithUser(User user) {
        return agsfjope.backend.core.entities.AuditLog.builder()
                .auditLogId(UUID.randomUUID())
                .user(user)
                .action(agsfjope.backend.core.enums.AuditAction.LOGIN)
                .entityType("User")
                .entityId(user.getUserId())
                .ipAddress("192.168.1.10")
                .createdAt(OffsetDateTime.now().minusMinutes(5))
                .build();
    }

    /**
     * AuditLog của hệ thống (user = null → hiển thị "system"/"System").
     */
    public static agsfjope.backend.core.entities.AuditLog createSystemAuditLog() {
        return agsfjope.backend.core.entities.AuditLog.builder()
                .auditLogId(UUID.randomUUID())
                .user(null)
                .action(agsfjope.backend.core.enums.AuditAction.CONFIG_CHANGE)
                .entityType("SystemConfig")
                .entityId(UUID.randomUUID())
                .ipAddress("127.0.0.1")
                .createdAt(OffsetDateTime.now().minusMinutes(1))
                .build();
    }
}
