package agsfjope.backend.application.dtos.responses.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO representing a single in-app notification returned to the client.
 * <p>
 * Contains the notification details including deep-link fields
 * ({@code relatedEntityType} and {@code relatedEntityId}) so the frontend
 * can navigate to the related page (exam, result, appeal, etc.) when the
 * user clicks on the notification.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    /** Unique identifier for this notification. */
    private UUID notificationId;

    /** Short title shown in the notification list (e.g. "Kỳ thi đã mở"). */
    private String title;

    /** Full body text (e.g. "Kỳ thi HK1-2024-2025 đã bắt đầu, bạn có thể nộp bài."). */
    private String body;

    /**
     * Type of the related domain entity for deep-linking.
     * Values: EXAM, GRADING_RESULT, APPEAL, SUBMISSION, PAYMENT.
     * May be null if the notification is not linked to a specific entity.
     */
    private String relatedEntityType;

    /**
     * UUID of the related domain entity for deep-linking.
     * The frontend uses this with {@code relatedEntityType} to build the navigation URL.
     */
    private UUID relatedEntityId;

    /** Whether this notification has been marked as read by the user. */
    private Boolean isRead;

    /** Timestamp when the notification was created. */
    private OffsetDateTime createdAt;
}
