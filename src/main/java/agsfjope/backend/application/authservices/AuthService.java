package agsfjope.backend.application.authservices;

import agsfjope.backend.application.dtos.requests.auth.ForgotPasswordRequest;
import agsfjope.backend.application.dtos.requests.auth.LoginRequest;
import agsfjope.backend.application.dtos.requests.auth.RefreshTokenRequest;
import agsfjope.backend.application.dtos.requests.auth.RegisterRequest;
import agsfjope.backend.application.dtos.requests.auth.ResetPasswordRequest;
import agsfjope.backend.application.dtos.responses.auth.LoginResponse;
import agsfjope.backend.application.dtos.responses.auth.UserProfileResponse;

/**
 * Service interface for authentication-related use cases.
 * Defines the contract for Login, Refresh Token, Logout, Get Profile, and Forgot/Reset Password flows.
 * Following Clean Architecture: the application layer defines the interface,
 * infrastructure/implementation classes provide the actual logic.
 */
public interface AuthService {

    /**
     * Processes a login request by validating credentials and issuing JWT tokens.
     * @param request the login request containing username and password
     * @return a LoginResponse containing the access token, refresh token, and user info
     */
    LoginResponse login(LoginRequest request);

    /**
     * Validates a refresh token and issues a new access token (SD_01_2).
     * The existing refresh token is kept unchanged and returned as-is.
     * @param request the request body containing the raw refresh token string
     * @return a LoginResponse with a fresh access token and the same refresh token
     */
    LoginResponse refreshToken(RefreshTokenRequest request);

    /**
     * Revokes all refresh tokens for the given user (SD_01_3 - Logout).
     * The access token itself is stateless JWT and is NOT blacklisted (by design).
     * @param username the username extracted from the authenticated SecurityContext
     */
    void logout(String username);

    /**
     * Returns the profile information of the currently authenticated user.
     * @param username extracted from SecurityContextHolder by the controller
     * @return a UserProfileResponse DTO (excludes password hash and internal fields)
     */
    UserProfileResponse getUserProfile(String username);

    /**
     * Initiates the Forgot Password flow (Chặng A):
     * generates a PasswordResetToken and sends a reset-link email.
     * @param request DTO containing the user's email address
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * Validates a password-reset token without consuming it (Chặng B).
     * Called by the frontend when the /reset-password page first loads.
     * Throws InvalidTokenException (401) if not found or already used.
     * Throws TokenExpiredException (401) if expired.
     * @param token the raw UUID token string from the URL query param
     */
    void verifyResetToken(String token);

    /**
     * Completes the password-reset flow (Chặng C):
     * re-validates the token, hashes the new password, and marks the token as used.
     * @param request DTO containing token, newPassword, confirmPassword
     */
    void resetPassword(ResetPasswordRequest request);

    // ─────────────────────────────────────────────────────────────────────────
    // Registration & Account Activation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Registers a new student account.
     * Validates the FPT email, derives and cross-checks username and MSSV,
     * saves the user with IsActive=false, and sends a 24-hour activation link by email.
     * @param request DTO containing email, username, mssv, fullName, password
     */
    void register(RegisterRequest request);

    /**
     * Activates a newly registered account by verifying the activation JWT.
     * Sets IsActive=true and EmailVerifiedAt on the User entity.
     * @param token the activation JWT from the verify-account page query param
     */
    void verifyAccount(String token);
}
