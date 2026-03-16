package agsfjope.backend.core.exceptions.config;

/**
 * Exception thrown when provided configuration payload is invalid.
 * Example: grading weights total is not equal to 100%.
 * Handled by GlobalExceptionHandler as HTTP 400.
 */
public class InvalidConfigException extends RuntimeException {

    public InvalidConfigException(String message) {
        super(message);
    }
}
