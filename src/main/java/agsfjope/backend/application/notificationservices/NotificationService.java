package agsfjope.backend.application.notificationservices;

import agsfjope.backend.application.dtos.responses.notification.NotificationResponse;
import agsfjope.backend.application.dtos.responses.notification.UnreadCountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for in-app Notification Center operations.
 * <p>
 * Covers NOTI-001 (Dispatch), NOTI-003 (Read Status), and SCH-005 (Cleanup).
 * All methods operate on the currently authenticated user's notifications
 * unless specified otherwise (e.g. the internal {@code createNotification} method).
 * </p>
 */
public interface NotificationService {

    /**
     * NOTI-003: Returns notifications for the current user based on the filter.
     *
     * @param filter "all" — all notifications, "unread" — only unread, "read" — only read
     * @return list of matching notifications, newest first
     */
    List<NotificationResponse> getMyNotifications(String filter);

    /**
     * DEV NOTE:
     * New paged version for Notification Center.
     * Added without removing the old List-based method to avoid breaking
     * existing frontend code immediately.
     *
     * @param filter   "all" — all notifications, "unread" — only unread, "read" — only read
     * @param pageable page/size/sort info
     * @return paged notifications, newest first by requested sort
     */
    Page<NotificationResponse> getMyNotificationsPaged(String filter, Pageable pageable);

    /**
     * NOTI-003: Returns the count of unread notifications for the current user.
     * Used to render the badge icon on the notification bell.
     *
     * @return wrapper DTO containing the unread count
     */
    UnreadCountResponse getUnreadCount();

    /**
     * NOTI-003: Marks a single notification as read, setting the readAt timestamp.
     * Only the owning user can mark their own notification.
     *
     * @param notificationId UUID of the notification to mark as read
     * @throws agsfjope.backend.core.exceptions.ResourceNotFoundException if not found or not owned by the current user
     */
    void markAsRead(UUID notificationId);

    /**
     * NOTI-003: Marks ALL unread notifications of the current user as read.
     * Corresponds to the "Mark all as read" action in the Notification Center.
     */
    void markAllAsRead();

    /**
     * NOTI-001: Internal method — creates and persists a new in-app notification for a user.
     * <p>
     * Called by other services (ExamService, AppealService, GradingService, etc.)
     * when domain events occur that should notify the user.
     * </p>
     *
     * @param userId            the recipient user's UUID
     * @param title             short notification title
     * @param body              full notification body text
     * @param relatedEntityType entity type for deep-linking (EXAM, APPEAL, SUBMISSION, PAYMENT, GRADING_RESULT)
     * @param relatedEntityId   UUID of the related entity (may be null)
     */
    void createNotification(UUID userId, String title, String body,
                            String relatedEntityType, UUID relatedEntityId);

    /**
     * SCH-005: Deletes read notifications older than 30 days.
     * Called weekly by {@code NotificationCleanupScheduler}.
     *
     * @return the number of deleted notification records
     */
    int cleanupOldNotifications();
}