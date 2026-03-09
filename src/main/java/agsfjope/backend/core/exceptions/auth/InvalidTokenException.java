package agsfjope.backend.core.exceptions.auth;

/**
 * Exception thrown when a Refresh Token is not found in the database or has been revoked (isRevoked = true).
 * Mapped to HTTP 401 Unauthorized by GlobalExceptionHandler.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
