package agsfjope.backend.core.exceptions.auth;

/**
 * Exception thrown when a requested resource (User, Token, etc.) is not found in the database.
 * Used by GlobalExceptionHandler to return HTTP 404.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
