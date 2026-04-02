package agsfjope.backend.application.authservices.impl;

import agsfjope.backend.application.dtos.requests.auth.*;
import agsfjope.backend.application.dtos.responses.auth.LoginResponse;
import agsfjope.backend.application.dtos.responses.auth.UserProfileResponse;
import agsfjope.backend.application.ports.out.EmailService;
import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.exceptions.auth.*;
import agsfjope.backend.core.repositories.auth.*;
import agsfjope.backend.infrastructure.security.jwt.JwtTokenProvider;
import agsfjope.backend.testutils.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests cho AuthServiceImpl — 22 test cases (N/A/B).
 * Pattern: AAA (Arrange - Act - Assert)
 * Tên method: methodName_Condition_ExpectedBehavior
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;

    @InjectMocks
    private AuthServiceImpl authService;

    // =========================================================================
    // login()  — [N] 1 normal, [A] 4 abnormal
    // =========================================================================

    @Test
    @DisplayName("[N] login - Đăng nhập thành công với thông tin hợp lệ")
    void login_ValidCredentials_ReturnsLoginResponse() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User user = TestDataFactory.createActiveStudent();
        LoginRequest request = new LoginRequest();
        request.setUsername(user.getUsername());
        request.setPassword("rawPassword");

        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("rawPassword", user.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-token-abc");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token-xyz");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(new RefreshToken());

        // ── Act ───────────────────────────────────────────────────────────────
        LoginResponse response = authService.login(request);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals("access-token-abc", response.getAccessToken());
        assertEquals("refresh-token-xyz", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        verify(refreshTokenRepository).revokeAllByUser(user);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("[A] login - Username không tồn tại → UnauthorizedException")
    void login_UserNotFound_ThrowsUnauthorizedException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        LoginRequest request = new LoginRequest();
        request.setUsername("nonexistent");
        request.setPassword("anyPassword");
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(UnauthorizedException.class, () -> authService.login(request));
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("[A] login - Tài khoản chưa kích hoạt → AccountNotVerifiedException")
    void login_InactiveAccount_ThrowsAccountNotVerifiedException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User user = TestDataFactory.createInactiveStudent();
        LoginRequest request = new LoginRequest();
        request.setUsername(user.getUsername());
        request.setPassword("anyPassword");
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(AccountNotVerifiedException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("[A] login - Tài khoản đang bị khóa → AccountLockedException")
    void login_LockedAccount_ThrowsAccountLockedException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User user = TestDataFactory.createLockedStudent();
        LoginRequest request = new LoginRequest();
        request.setUsername(user.getUsername());
        request.setPassword("anyPassword");
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(AccountLockedException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("[A] login - Mật khẩu sai → UnauthorizedException")
    void login_WrongPassword_ThrowsUnauthorizedException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User user = TestDataFactory.createActiveStudent();
        LoginRequest request = new LoginRequest();
        request.setUsername(user.getUsername());
        request.setPassword("wrongPassword");
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", user.getPasswordHash())).thenReturn(false);

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(UnauthorizedException.class, () -> authService.login(request));
        verify(refreshTokenRepository, never()).revokeAllByUser(any());
    }

    // =========================================================================
    // refreshToken()  — [N] 1 normal, [A] 2 abnormal, [B] 1 boundary
    // =========================================================================

    @Test
    @DisplayName("[N] refreshToken - Làm mới token thành công")
    void refreshToken_ValidToken_ReturnsNewAccessToken() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User user = TestDataFactory.createActiveStudent();
        RefreshToken token = TestDataFactory.createValidRefreshToken(user);
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(token.getTokenHash());

        when(refreshTokenRepository.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("new-access-token");

        // ── Act ───────────────────────────────────────────────────────────────
        LoginResponse response = authService.refreshToken(request);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
        assertEquals(token.getTokenHash(), response.getRefreshToken());
    }

    @Test
    @DisplayName("[A] refreshToken - Token không tồn tại → InvalidTokenException")
    void refreshToken_TokenNotFound_ThrowsInvalidTokenException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("fake-token");
        when(refreshTokenRepository.findByTokenHash("fake-token")).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(InvalidTokenException.class, () -> authService.refreshToken(request));
    }

    @Test
    @DisplayName("[A] refreshToken - Token đã bị revoked → InvalidTokenException")
    void refreshToken_RevokedToken_ThrowsInvalidTokenException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User user = TestDataFactory.createActiveStudent();
        RefreshToken token = TestDataFactory.createRevokedRefreshToken(user);
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(token.getTokenHash());
        when(refreshTokenRepository.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(InvalidTokenException.class, () -> authService.refreshToken(request));
    }

    @Test
    @DisplayName("[B] refreshToken - Token vừa hết hạn → TokenExpiredException + đánh dấu revoked")
    void refreshToken_ExpiredToken_ThrowsTokenExpiredAndMarksRevoked() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User user = TestDataFactory.createActiveStudent();
        RefreshToken token = TestDataFactory.createExpiredRefreshToken(user);
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(token.getTokenHash());
        when(refreshTokenRepository.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(token);

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(TokenExpiredException.class, () -> authService.refreshToken(request));
        verify(refreshTokenRepository).save(argThat(t -> Boolean.TRUE.equals(t.getIsRevoked())));
    }

    // =========================================================================
    // logout()  — [N] 1 normal, [A] 1 abnormal
    // =========================================================================

    @Test
    @DisplayName("[N] logout - Đăng xuất thành công, revoke tất cả tokens")
    void logout_ValidUser_RevokesAllTokens() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User user = TestDataFactory.createActiveStudent();
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertDoesNotThrow(() -> authService.logout(user.getUsername()));
        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    @DisplayName("[A] logout - Username không tồn tại → UnauthorizedException")
    void logout_UserNotFound_ThrowsUnauthorizedException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(UnauthorizedException.class, () -> authService.logout("ghost"));
        verify(refreshTokenRepository, never()).revokeAllByUser(any());
    }

    // =========================================================================
    // getUserProfile()  — [N] 1 normal, [A] 1 abnormal
    // =========================================================================

    @Test
    @DisplayName("[N] getUserProfile - Lấy profile thành công")
    void getUserProfile_ValidUsername_ReturnsProfile() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User user = TestDataFactory.createActiveStudent();
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        // ── Act ───────────────────────────────────────────────────────────────
        UserProfileResponse profile = authService.getUserProfile(user.getUsername());

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(profile);
        assertEquals(user.getUsername(), profile.getUsername());
        assertEquals(user.getEmail(), profile.getEmail());
        assertEquals("STUDENT", profile.getRoleName());
    }

    @Test
    @DisplayName("[A] getUserProfile - Username không tồn tại → NotFoundException")
    void getUserProfile_UserNotFound_ThrowsNotFoundException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(NotFoundException.class, () -> authService.getUserProfile("nobody"));
    }

    // =========================================================================
    // forgotPassword()  — [N] 1 normal, [A] 1 abnormal
    // =========================================================================

    @Test
    @DisplayName("[N] forgotPassword - Gửi email reset password thành công")
    void forgotPassword_ValidEmail_SavesTokenAndSendsEmail() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User user = TestDataFactory.createActiveStudent();
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenReturn(new PasswordResetToken());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertDoesNotThrow(() -> authService.forgotPassword(request));
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq(user.getEmail()), anyString());
    }

    @Test
    @DisplayName("[A] forgotPassword - Email không tồn tại → NotFoundException")
    void forgotPassword_EmailNotFound_ThrowsNotFoundException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("unknown@fpt.edu.vn");
        when(userRepository.findByEmail("unknown@fpt.edu.vn")).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(NotFoundException.class, () -> authService.forgotPassword(request));
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    // =========================================================================
    // verifyResetToken()  — [N] 1 normal, [A] 1 abnormal, [B] 1 boundary
    // =========================================================================

    @Test
    @DisplayName("[N] verifyResetToken - Token hợp lệ → không ném exception")
    void verifyResetToken_ValidToken_NoException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User user = TestDataFactory.createActiveStudent();
        PasswordResetToken token = TestDataFactory.createValidPasswordResetToken(user);
        when(passwordResetTokenRepository.findByTokenHash(token.getTokenHash()))
                .thenReturn(Optional.of(token));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertDoesNotThrow(() -> authService.verifyResetToken(token.getTokenHash()));
    }

    @Test
    @DisplayName("[A] verifyResetToken - Token đã dùng → InvalidTokenException")
    void verifyResetToken_UsedToken_ThrowsInvalidTokenException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User user = TestDataFactory.createActiveStudent();
        PasswordResetToken token = TestDataFactory.createUsedPasswordResetToken(user);
        when(passwordResetTokenRepository.findByTokenHash(token.getTokenHash()))
                .thenReturn(Optional.of(token));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(InvalidTokenException.class,
                () -> authService.verifyResetToken(token.getTokenHash()));
    }

    @Test
    @DisplayName("[B] verifyResetToken - Token vừa hết hạn → TokenExpiredException")
    void verifyResetToken_ExpiredToken_ThrowsTokenExpiredException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User user = TestDataFactory.createActiveStudent();
        PasswordResetToken token = TestDataFactory.createExpiredPasswordResetToken(user);
        when(passwordResetTokenRepository.findByTokenHash(token.getTokenHash()))
                .thenReturn(Optional.of(token));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(TokenExpiredException.class,
                () -> authService.verifyResetToken(token.getTokenHash()));
    }

    // =========================================================================
    // resetPassword()  — [N] 1 normal, [A] 1 abnormal
    // =========================================================================

    @Test
    @DisplayName("[N] resetPassword - Đặt lại mật khẩu thành công, đánh dấu token dùng rồi")
    void resetPassword_ValidToken_UpdatesPasswordAndMarksUsed() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User user = TestDataFactory.createActiveStudent();
        PasswordResetToken token = TestDataFactory.createValidPasswordResetToken(user);
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken(token.getTokenHash());
        request.setNewPassword("NewPassword123!");

        when(passwordResetTokenRepository.findByTokenHash(token.getTokenHash()))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("$2a$10$newHashedPwd");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenReturn(token);

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertDoesNotThrow(() -> authService.resetPassword(request));
        verify(passwordEncoder).encode("NewPassword123!");
        verify(userRepository).save(argThat(u -> "$2a$10$newHashedPwd".equals(u.getPasswordHash())));
        verify(passwordResetTokenRepository).save(argThat(t -> Boolean.TRUE.equals(t.getIsUsed())));
    }

    @Test
    @DisplayName("[A] resetPassword - Token đã dùng → InvalidTokenException")
    void resetPassword_UsedToken_ThrowsInvalidTokenException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User user = TestDataFactory.createActiveStudent();
        PasswordResetToken token = TestDataFactory.createUsedPasswordResetToken(user);
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken(token.getTokenHash());
        request.setNewPassword("anything");
        when(passwordResetTokenRepository.findByTokenHash(token.getTokenHash()))
                .thenReturn(Optional.of(token));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(InvalidTokenException.class, () -> authService.resetPassword(request));
        verify(userRepository, never()).save(any());
    }

    // =========================================================================
    // register()  — [N] 1 normal, [A] 3 abnormal
    // =========================================================================

    @Test
    @DisplayName("[N] register - Đăng ký tài khoản sinh viên thành công")
    void register_ValidStudentInfo_SavesUserAndSendsActivationEmail() {
        // ── Arrange ──────────────────────────────────────────────────────────
        RegisterRequest request = new RegisterRequest();
        request.setEmail("lamtvse173173@fpt.edu.vn");
        request.setUsername("lamtvse173173");
        request.setMssv("se173173");
        request.setFullName("Lam Tran Van");
        request.setPassword("Pass@1234");

        Role studentRole = TestDataFactory.createStudentRole();
        when(userRepository.findByEmail("lamtvse173173@fpt.edu.vn")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("lamtvse173173")).thenReturn(Optional.empty());
        when(userRepository.findByMssv("se173173")).thenReturn(Optional.empty());
        when(roleRepository.findByName("STUDENT")).thenReturn(Optional.of(studentRole));
        when(passwordEncoder.encode("Pass@1234")).thenReturn("$2a$10$hashedPwd");
        when(userRepository.save(any(User.class))).thenReturn(new User());
        when(jwtTokenProvider.generateActivationToken("lamtvse173173@fpt.edu.vn"))
                .thenReturn("activation-token");

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertDoesNotThrow(() -> authService.register(request));
        verify(userRepository).save(argThat(u ->
                Boolean.FALSE.equals(u.getIsActive()) && "se173173".equals(u.getMssv())));
        verify(emailService).sendActivationEmail(eq("lamtvse173173@fpt.edu.vn"), anyString());
    }

    @Test
    @DisplayName("[A] register - Username không khớp email FPT → IllegalArgumentException")
    void register_UsernameMismatch_ThrowsIllegalArgumentException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        RegisterRequest request = new RegisterRequest();
        request.setEmail("lamtvse173173@fpt.edu.vn");
        request.setUsername("wrongname");  // Không khớp alias email
        request.setMssv("se173173");
        request.setPassword("Pass@1234");

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] register - MSSV không khớp email FPT → IllegalArgumentException")
    void register_MssvMismatch_ThrowsIllegalArgumentException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        RegisterRequest request = new RegisterRequest();
        request.setEmail("lamtvse173173@fpt.edu.vn");
        request.setUsername("lamtvse173173");
        request.setMssv("se999999");  // MSSV sai
        request.setPassword("Pass@1234");

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
    }

    @Test
    @DisplayName("[A] register - Email đã tồn tại trong hệ thống → IllegalArgumentException")
    void register_DuplicateEmail_ThrowsIllegalArgumentException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        RegisterRequest request = new RegisterRequest();
        request.setEmail("lamtvse173173@fpt.edu.vn");
        request.setUsername("lamtvse173173");
        request.setMssv("se173173");
        request.setPassword("Pass@1234");
        when(userRepository.findByEmail("lamtvse173173@fpt.edu.vn"))
                .thenReturn(Optional.of(new User()));  // Email đã tồn tại

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }
}
