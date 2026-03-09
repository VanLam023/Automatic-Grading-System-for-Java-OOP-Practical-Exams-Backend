package agsfjope.backend.core.exceptions.auth;

/**
 * Exception thrown when a Refresh Token has passed its expiry date (expiresAt < now).
 * Mapped to HTTP 401 Unauthorized by GlobalExceptionHandler.
 * When this is thrown, the caller must also mark the token as isRevoked = true in the DB.
 */
public class TokenExpiredException extends RuntimeException {

    public TokenExpiredException(String message) {
        super(message);
    }
}
