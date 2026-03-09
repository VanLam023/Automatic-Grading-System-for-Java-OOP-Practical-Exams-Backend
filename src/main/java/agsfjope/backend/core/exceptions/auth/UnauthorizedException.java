package agsfjope.backend.core.exceptions.auth;

/**
 * Exception thrown when user credentials are invalid (username not found or password mismatch).
 * Used by GlobalExceptionHandler to return HTTP 401.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
