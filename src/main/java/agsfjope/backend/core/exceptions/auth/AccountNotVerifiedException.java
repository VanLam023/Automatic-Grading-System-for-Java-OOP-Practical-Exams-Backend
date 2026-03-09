package agsfjope.backend.core.exceptions.auth;

/**
 * Exception thrown when user account has not been activated/verified yet.
 * Used by GlobalExceptionHandler to return HTTP 403.
 */
public class AccountNotVerifiedException extends RuntimeException {

    public AccountNotVerifiedException(String message) {
        super(message);
    }
}
