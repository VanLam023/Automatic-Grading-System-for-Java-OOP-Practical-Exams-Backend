package agsfjope.backend.application.authservices.impl;

import agsfjope.backend.application.authservices.AuthService;
import agsfjope.backend.application.dtos.requests.auth.ForgotPasswordRequest;
import agsfjope.backend.application.dtos.requests.auth.LoginRequest;
import agsfjope.backend.application.dtos.requests.auth.RefreshTokenRequest;
import agsfjope.backend.application.dtos.requests.auth.RegisterRequest;
import agsfjope.backend.application.dtos.requests.auth.ResetPasswordRequest;
import agsfjope.backend.application.dtos.responses.auth.LoginResponse;
import agsfjope.backend.application.dtos.responses.auth.UserProfileResponse;
import agsfjope.backend.application.ports.out.EmailService;
import agsfjope.backend.core.entities.PasswordResetToken;
import agsfjope.backend.core.entities.RefreshToken;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.exceptions.auth.AccountLockedException;
import agsfjope.backend.core.exceptions.auth.AccountNotVerifiedException;
import agsfjope.backend.core.exceptions.auth.InvalidTokenException;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.exceptions.auth.TokenExpiredException;
import agsfjope.backend.core.exceptions.auth.UnauthorizedException;
import agsfjope.backend.core.repositories.auth.PasswordResetTokenRepository;
import agsfjope.backend.core.repositories.auth.RefreshTokenRepository;
import agsfjope.backend.core.repositories.auth.RoleRepository;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.infrastructure.security.jwt.JwtTokenProvider;
import agsfjope.backend.infrastructure.audit.Auditable;
import agsfjope.backend.infrastructure.audit.AuditLogHelper;
import agsfjope.backend.core.enums.AuditAction;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Implementation of AuthService.
 * Contains all the business logic for authenticating a user, refreshing tokens,
 * and logging out, following the flows defined in SD_01_1, SD_01_2, SD_01_3.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RoleRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditLogHelper auditLogHelper;

    /**
     * Full login flow following SD_01_1_Login:
     * 1. Find user by username — throw UnauthorizedException (401) if not found.
     * 2. Check isActive — throw AccountNotVerifiedException (403) if inactive.
     * 3. Check isLocked + lockedUntil — throw AccountLockedException (423) if locked.
     * 4. Verify password — throw UnauthorizedException (401) if mismatch.
     * 5. Update lastLoginAt timestamp.
     * 6. Revoke all previous refresh tokens, issue new Access Token + Refresh Token.
     * 7. Save new refresh token to DB and return the LoginResponse.
     *
     * @param request DTO containing username and password from the client
     * @return LoginResponse containing access token, refresh token, and user info
     */
    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {

        // === STEP 1: Find user by username ===
        // If user doesn't exist at all, we return the same generic "invalid credentials" message
        // to prevent username enumeration attacks (never reveal whether username exists or not)
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("MSG-01: Tên đăng nhập hoặc mật khẩu không đúng"));

        // === STEP 2: Check if account is activated ===
        // isActive is set to false by default; admin must activate the account first
        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new AccountNotVerifiedException("MSG-03: Tài khoản chưa được kích hoạt");
        }

        // === STEP 3: Check if account is currently locked ===
        // isLocked = true AND lockedUntil is in the future means the ban is still active
        if (Boolean.TRUE.equals(user.getIsLocked())
                && user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
            throw new AccountLockedException("MSG-04: Tài khoản bị khóa đến " + user.getLockedUntil());
        }

        // === STEP 4: Verify the provided password against the stored hash ===
        // BCryptPasswordEncoder.matches() compares raw text with the hashed version
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("MSG-01: Tên đăng nhập hoặc mật khẩu không đúng");
        }

        // === STEP 5: Password is correct — update the last login timestamp ===
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        // === STEP 6: Revoke all existing refresh tokens for this user (single-session policy) ===
        // This ensures a user cannot have multiple active sessions at the same time
        refreshTokenRepository.revokeAllByUser(user);

        // === STEP 7a: Generate new JWT Access Token (valid for 4 hours) ===
        String accessToken = jwtTokenProvider.generateAccessToken(user);

        // === STEP 7b: Generate new Refresh Token (random UUID) ===
        String rawRefreshToken = jwtTokenProvider.generateRefreshToken();

        // === STEP 7c: Save the new Refresh Token to the database ===
        // Refresh tokens expire in 7 days from now
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .tokenHash(rawRefreshToken)
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .isRevoked(false)
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        // === STEP 8: Build and return the LoginResponse DTO ===
        
        // --- MANUAL AUDIT LOG FOR LOGIN ---
        auditLogHelper.logWithExplicitUser(user.getUserId(), AuditAction.LOGIN, "USER", user.getUserId(), null, null);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(JwtTokenProvider.ACCESS_TOKEN_EXPIRY_SECONDS)
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .roleName(user.getRole().getName())
                .build();
    }

    /**
     * Refresh Token flow following SD_01_2:
     * 1. Look up the refresh token in DB by its hash string.
     *    - If not found or isRevoked == true  -> throw InvalidTokenException (401).
     * 2. Check expiry:
     *    - If expiresAt is before now -> mark token as revoked in DB, throw TokenExpiredException (401).
     * 3. Validate the owner user's account state:
     *    - If isActive == false          -> throw AccountNotVerifiedException (403).
     *    - If isLocked and still in lock -> throw AccountLockedException (423).
     * 4. Generate a brand-new Access Token for the user.
     * 5. Return the SAME refresh token (no rotation by design — per SD_01_2 note).
     *
     * @param request DTO containing the raw refresh token string sent by the client
     * @return LoginResponse with a new accessToken and the unchanged refreshToken
     */
    @Override
    @Transactional
    public LoginResponse refreshToken(RefreshTokenRequest request) {

        // === STEP 1: Look up the token in the database ===
        // findByTokenHash returns empty if the token string was never issued or already purged
        RefreshToken tokenEntity = refreshTokenRepository
                .findByTokenHash(request.getRefreshToken())
                .orElseThrow(() -> new InvalidTokenException("MSG-07: Refresh token không hợp lệ"));

        // Also reject if the token was already revoked (e.g. user logged out previously)
        if (Boolean.TRUE.equals(tokenEntity.getIsRevoked())) {
            throw new InvalidTokenException("MSG-07: Refresh token không hợp lệ");
        }

        // === STEP 2: Check if the token has expired ===
        // If expired: first persist the revoked state, THEN throw the exception
        if (tokenEntity.getExpiresAt().isBefore(OffsetDateTime.now())) {
            // Mark as revoked so this token can never be reused
            tokenEntity.setIsRevoked(true);
            refreshTokenRepository.save(tokenEntity);
            throw new TokenExpiredException("MSG-08: Refresh token đã hết hạn");
        }

        // === STEP 3: Validate the user's account state ===
        // Leverage JPA @ManyToOne — user is already loaded from the token entity
        User user = tokenEntity.getUser();

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new AccountNotVerifiedException("MSG-03: Tài khoản chưa được kích hoạt");
        }

        if (Boolean.TRUE.equals(user.getIsLocked())
                && user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
            throw new AccountLockedException("MSG-04: Tài khoản bị khóa đến " + user.getLockedUntil());
        }

        // === STEP 4: Generate a fresh Access Token ===
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);

        // === STEP 5: Return response — refresh token is UNCHANGED per SD_01_2 design ===
        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(request.getRefreshToken())
                .tokenType("Bearer")
                .expiresIn(JwtTokenProvider.ACCESS_TOKEN_EXPIRY_SECONDS)
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .roleName(user.getRole().getName())
                .build();
    }

    /**
     * Logout flow following SD_01_3:
     * 1. Look up the User by username (extracted from authenticated SecurityContext by controller).
     * 2. Revoke ALL refresh tokens for this user so they cannot silently re-authenticate.
     * 3. The JWT Access Token itself is stateless — it cannot be blacklisted without extra infra.
     *    Per diagram note, blacklisting is intentionally skipped for now.
     *
     * @param username the username of the currently authenticated user (from SecurityContext)
     */
    @Override
    @Transactional
    @Auditable(action = AuditAction.LOGOUT, entityType = "USER")
    public void logout(String username) {

        // === STEP 1: Load the User entity from DB ===
        // Use the same unauthorized exception pattern to avoid leaking user info
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("MSG-01: Không tìm thấy người dùng"));

        // === STEP 2: Revoke all active refresh tokens for this user ===
        // This invalidates all "remember me" sessions across all devices
        refreshTokenRepository.revokeAllByUser(user);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/auth/me  →  getUserProfile
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the profile DTO for the currently authenticated user.
     * Maps User entity fields to UserProfileResponse (password hash is never included).
     *
     * @param username extracted from SecurityContextHolder.getContext().getAuthentication().getName()
     * @return UserProfileResponse DTO
     */
    @Override
    public UserProfileResponse getUserProfile(String username) {

        // Load the user entity from DB
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Kh\u00f4ng t\u00ecm th\u1ea5y t\u00e0i kho\u1ea3n"));

        // Map to DTO — role name comes from the Role entity (lazy loaded, but OK here because @Transactional not needed for a single read)
        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roleName(user.getRole().getName())
                .mssv(user.getMssv())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/auth/forgot-password  →  forgotPassword  (Ch\u1eb7ng A)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Forgot Password flow Ch\u1eb7ng A:
     * 1. Find User by email — throw NotFoundException (404) if not found.
     * 2. Generate a random UUID token and persist it (ExpiresAt = NOW + 15 min, IsUsed = false).
     * 3. Build the reset link and send a branded HTML email via EmailService.
     *
     * @param request DTO containing the email address from the request body
     */
    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {

        // === STEP 1: Look up user by email ===
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new NotFoundException(
                        "Kh\u00f4ng t\u00ecm th\u1ea5y t\u00e0i kho\u1ea3n v\u1edbi email n\u00e0y"));

        // === STEP 2: Generate a raw UUID token and save to PasswordResetTokens table ===
        String rawToken = UUID.randomUUID().toString();

        PasswordResetToken tokenEntity = PasswordResetToken.builder()
                .user(user)
                .tokenHash(rawToken)
                .expiresAt(OffsetDateTime.now().plusMinutes(15))
                .isUsed(false)
                .build();
        passwordResetTokenRepository.save(tokenEntity);

        // === STEP 3: Build reset link and send email ===
        String resetLink = "http://localhost:5173/reset-password?token=" + rawToken;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/auth/verify-reset-token  →  verifyResetToken  (Ch\u1eb7ng B)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Forgot Password flow Ch\u1eb7ng B — Token Verification (called when user clicks the link):
     * 1. Find token by hash — throw InvalidTokenException (401) if not found.
     * 2. Check IsUsed — throw InvalidTokenException (401) if already consumed.
     * 3. Check expiry — throw TokenExpiredException (401) if expired.
     * 4. Return normally (200 OK) — frontend then renders the new-password form.
     *
     * @param token the raw UUID string from the URL query param (?token=...)
     */
    @Override
    public void verifyResetToken(String token) {

        // === STEP 1 & 2: Look up and check revoked/used status ===
        PasswordResetToken tokenEntity = passwordResetTokenRepository.findByTokenHash(token)
                .orElseThrow(() -> new InvalidTokenException(
                        "Link kh\u00f4i ph\u1ee5c kh\u00f4ng h\u1ee3p l\u1ec7 ho\u1eb7c \u0111\u00e3 \u0111\u01b0\u1ee3c s\u1eed d\u1ee5ng"));

        if (Boolean.TRUE.equals(tokenEntity.getIsUsed())) {
            throw new InvalidTokenException(
                    "Link kh\u00f4i ph\u1ee5c kh\u00f4ng h\u1ee3p l\u1ec7 ho\u1eb7c \u0111\u00e3 \u0111\u01b0\u1ee3c s\u1eed d\u1ee5ng");
        }

        // === STEP 3: Check token expiry ===
        if (tokenEntity.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new TokenExpiredException(
                    "Link kh\u00f4i ph\u1ee5c \u0111\u00e3 h\u1ebft h\u1ea1n. Vui l\u00f2ng y\u00eau c\u1ea7u c\u1ea5p l\u1ea1i");
        }
        // If all checks pass → return normally (200 OK)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/auth/reset-password  →  resetPassword  (Ch\u1eb7ng C)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Forgot Password flow Ch\u1eb7ng C — Confirm New Password:
     * 1. Re-validate the token (same checks as Ch\u1eb7ng B — guards against page lingering).
     * 2. Hash the new password with BCrypt and update the User entity.
     * 3. Mark the token as IsUsed = true so it cannot be replayed.
     *
     * @param request DTO containing token, newPassword, confirmPassword
     */
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {

        // === STEP 1: Re-validate the token (same logic as verifyResetToken) ===
        PasswordResetToken tokenEntity = passwordResetTokenRepository
                .findByTokenHash(request.getToken())
                .orElseThrow(() -> new InvalidTokenException(
                        "Link kh\u00f4i ph\u1ee5c kh\u00f4ng h\u1ee3p l\u1ec7 ho\u1eb7c \u0111\u00e3 \u0111\u01b0\u1ee3c s\u1eed d\u1ee5ng"));

        if (Boolean.TRUE.equals(tokenEntity.getIsUsed())) {
            throw new InvalidTokenException(
                    "Link kh\u00f4i ph\u1ee5c kh\u00f4ng h\u1ee3p l\u1ec7 ho\u1eb7c \u0111\u00e3 \u0111\u01b0\u1ee3c s\u1eed d\u1ee5ng");
        }

        if (tokenEntity.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new TokenExpiredException(
                    "Link kh\u00f4i ph\u1ee5c \u0111\u00e3 h\u1ebft h\u1ea1n. Vui l\u00f2ng y\u00eau c\u1ea7u c\u1ea5p l\u1ea1i");
        }

        // === STEP 2: Hash the new password and update the User ===
        User user = tokenEntity.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));

        // === STEP 2b: Auto-activate account if not yet active ===
        // This handles the flow where Admin creates an account and sends a reset-password link.
        // When the user resets their password, the account is automatically activated.
        if (Boolean.FALSE.equals(user.getIsActive())) {
            user.setIsActive(true);
            user.setIsLocked(false);
            if (user.getEmailVerifiedAt() == null) {
                user.setEmailVerifiedAt(OffsetDateTime.now());
            }
        }

        userRepository.save(user);

        // === STEP 3: Mark token as consumed so it cannot be replayed ===
        tokenEntity.setIsUsed(true);
        passwordResetTokenRepository.save(tokenEntity);
        
        // --- MANUAL AUDIT LOG FOR PASSWORD RESET ---
        auditLogHelper.logWithExplicitUser(user.getUserId(), AuditAction.UPDATE, "USER", user.getUserId(), null, null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/auth/register  →  register (Student self-registration)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Student self-registration flow:
     * 1. Validate FPT email format (already done by @Pattern on the DTO).
     * 2. Derive expectedUsername (part before '@') from email.
     *    - If username != expectedUsername → 400 Bad Request.
     * 3. Derive expectedMssv (last 8 chars of expectedUsername).
     *    - If mssv != expectedMssv → 400 Bad Request.
     * 4. Check email uniqueness; check mssv uniqueness.
     * 5. Resolve the "STUDENT" Role from DB (must exist from seed).
     * 6. Hash password and persist User with IsActive = false.
     * 7. Generate a 24-hour activation JWT and send it by email.
     *
     * @param request DTO containing email, username, mssv, fullName, password
     */
    @Override
    @Transactional
    public void register(RegisterRequest request) {

        // === STEP 2: Cross-check username against the FPT email alias ===
        // FPT email format: <username>@fpt.edu.vn
        String email = request.getEmail().trim().toLowerCase();
        String expectedUsername = email.substring(0, email.indexOf('@'));

        if (!request.getUsername().equalsIgnoreCase(expectedUsername)) {
            throw new IllegalArgumentException(
                    "Username phải trùng với tên định danh trong Email (" + expectedUsername + ")");
        }

        // === STEP 3: Cross-check MSSV against the last 8 chars of the email alias ===
        // Example: lamtvse173173 → last 8 chars = se173173 (2-letter code + 6-digit ID)
        if (expectedUsername.length() < 8) {
            throw new IllegalArgumentException("Email FPT không hợp lệ: alias quá ngắn");
        }
        String expectedMssv = expectedUsername.substring(expectedUsername.length() - 8);

        if (!request.getMssv().equalsIgnoreCase(expectedMssv)) {
            throw new IllegalArgumentException(
                    "Mã số sinh viên (MSSV) không hợp lệ so với Email cấu hình. MSSV đúng là: " + expectedMssv);
        }

        // === STEP 4: Check uniqueness — email and mssv must not already be taken ===
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email này đã được sử dụng. Vui lòng dùng email khác.");
        }
        if (userRepository.findByUsername(expectedUsername).isPresent()) {
            throw new IllegalArgumentException("Tài khoản với email này đã tồn tại.");
        }
        if (userRepository.findByMssv(request.getMssv()).isPresent()) {
            throw new IllegalArgumentException(
                    "MSSV \"" + request.getMssv() + "\" đã tồn tại trong hệ thống. Vui lòng kiểm tra lại.");
        }

        // === STEP 5: Resolve the STUDENT role (seeded into DB on startup) ===
        var studentRole = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new IllegalStateException("Role STUDENT không tồn tại. Vui lòng kiểm tra seed data."));

        // === STEP 6: Hash password and save user with IsActive = false ===
        User newUser = User.builder()
                .role(studentRole)
                .username(expectedUsername)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .mssv(request.getMssv())
                .isActive(false)    // Must click activation link before logging in
                .isLocked(false)
                .loginFailCount(0)
                .build();
        userRepository.save(newUser);

        // === STEP 7: Generate activation JWT and send email ===
        String activationToken = jwtTokenProvider.generateActivationToken(email);
        String activationLink = "http://localhost:5173/verify-account?token=" + activationToken;
        emailService.sendActivationEmail(email, activationLink);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/auth/verify-account  →  verifyAccount
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Account activation flow:
     * 1. Decode the activation JWT → extract email (any exception = 401 InvalidTokenException).
     * 2. Find User by email.
     * 3. If already active → return success (idempotent, in case user clicks link twice).
     * 4. Otherwise: set IsActive = true, set EmailVerifiedAt = NOW, persist.
     *
     * @param token the activation JWT from the verify-account URL query param
     */
    @Override
    @Transactional
    public void verifyAccount(String token) {

        // === STEP 1: Decode JWT and extract email ===
        String email;
        try {
            // getEmailFromActivationToken throws if JWT is expired or malformed
            email = jwtTokenProvider.getEmailFromActivationToken(token);
        } catch (Exception e) {
            throw new InvalidTokenException("Link kích hoạt không hợp lệ hoặc đã hết hạn. Vui lòng đăng ký lại.");
        }

        // === STEP 2: Find the user account by matching email ===
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản ứng với link kích hoạt này."));

        // === STEP 3: If already active, treat as success (idempotent) ===
        if (Boolean.TRUE.equals(user.getIsActive())) {
            // Account already verified — no need to do anything
            return;
        }

        // === STEP 4: Activate the account ===
        user.setIsActive(true);
        user.setEmailVerifiedAt(OffsetDateTime.now());
        userRepository.save(user);
    }
}
