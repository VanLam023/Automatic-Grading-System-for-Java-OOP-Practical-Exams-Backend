package agsfjope.backend.core.repositories.notification;

import agsfjope.backend.core.entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing {@link Notification} entities.
 * <p>
 * Provides data access methods for in-app notification operations:
 * listing, filtering by read status, counting unread, marking as read,
 * and scheduled cleanup of old notifications (SCH-005).
 * </p>
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Returns all notifications for a given user, sorted newest-first.
     * Used for the "All" filter tab in the Notification Center.
     *
     * @param userId the user's UUID
     * @return list of all notifications ordered by creation date DESC
     */
    List<Notification> findByUser_UserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Returns notifications for a user filtered by read status, sorted newest-first.
     * Used for "Unread" and "Read" filter tabs.
     *
     * @param userId the user's UUID
     * @param isRead true for read notifications, false for unread
     * @return filtered list ordered by creation date DESC
     */
    List<Notification> findByUser_UserIdAndIsReadOrderByCreatedAtDesc(UUID userId, Boolean isRead);

    /**
     * Counts the number of unread notifications for a user.
     * Used to render the badge count on the notification bell icon.
     *
     * @param userId the user's UUID
     * @param isRead pass {@code false} to count unread notifications
     * @return number of notifications matching the read status
     */
    long countByUser_UserIdAndIsRead(UUID userId, Boolean isRead);

    /**
     * Finds a specific notification that belongs to the given user.
     * Prevents users from accessing notifications belonging to other users.
     *
     * @param notificationId the notification UUID
     * @param userId         the owner user UUID
     * @return Optional containing the notification if found and owned by the user
     */
    Optional<Notification> findByNotificationIdAndUser_UserId(UUID notificationId, UUID userId);

    /**
     * SCH-005: Deletes all read notifications that were created before the cutoff date.
     * <p>
     * Called weekly by {@code NotificationCleanupScheduler} to purge notifications
     * older than 30 days that have already been read, reducing database load.
     * </p>
     *
     * @param cutoff the cutoff timestamp — notifications created before this time will be deleted
     * @return the number of deleted records
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.isRead = true AND n.createdAt < :cutoff")
    int deleteReadNotificationsOlderThan(@Param("cutoff") OffsetDateTime cutoff);
}
