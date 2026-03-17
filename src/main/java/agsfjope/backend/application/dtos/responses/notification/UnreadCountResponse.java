package agsfjope.backend.application.dtos.responses.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO containing the count of unread notifications.
 * <p>
 * Returned by the badge-count endpoint so the frontend can display
 * the unread count on the notification bell icon without loading the full list.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnreadCountResponse {

    /**
     * Number of unread notifications for the current user.
     * The frontend renders this as a badge on the notification bell.
     */
    private long unreadCount;
}
