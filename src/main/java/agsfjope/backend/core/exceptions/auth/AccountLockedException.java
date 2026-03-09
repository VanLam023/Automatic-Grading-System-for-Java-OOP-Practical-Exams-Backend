package agsfjope.backend.core.exceptions.auth;

/**
 * Exception thrown when user account is locked (isLocked = true AND lockedUntil > NOW()).
 * Used by GlobalExceptionHandler to return HTTP 423 Locked.
 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException(String message) {
        super(message);
    }
}
