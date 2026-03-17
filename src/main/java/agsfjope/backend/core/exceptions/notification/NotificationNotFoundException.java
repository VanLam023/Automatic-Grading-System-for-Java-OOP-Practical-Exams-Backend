package agsfjope.backend.core.exceptions.notification;

import java.util.UUID;

/**
 * Thrown when a notification is not found or does not belong to the requesting user.
 * Maps to HTTP 404 in {@code GlobalExceptionHandler}.
 */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(UUID notificationId) {
        super("Notification not found with id: " + notificationId);
    }
}
