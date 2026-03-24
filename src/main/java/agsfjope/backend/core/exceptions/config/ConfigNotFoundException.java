package agsfjope.backend.core.exceptions.config;

/**
 * Exception thrown when requested system configuration key or grading mode is not found.
 * Handled by GlobalExceptionHandler as HTTP 404.
 */
public class ConfigNotFoundException extends RuntimeException {

    public ConfigNotFoundException(String message) {
        super(message);
    }
}
