package agsfjope.backend.application.notificationservices.impl;

import agsfjope.backend.application.dtos.responses.notification.NotificationResponse;
import agsfjope.backend.application.dtos.responses.notification.UnreadCountResponse;
import agsfjope.backend.application.notificationservices.NotificationService;
import agsfjope.backend.core.entities.Notification;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.exceptions.notification.NotificationNotFoundException;
import agsfjope.backend.core.repositories.notification.NotificationRepository;
import agsfjope.backend.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of the {@link NotificationService} for in-app notification management.
 * <p>
 * Handles all operations in the Notification Center:
 * listing, badge count, mark-read, mark-all-read, notification creation (internal),
 * and scheduled cleanup (SCH-005).
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    // ─── Read Operations ─────────────────────────────────────────────────────

    /**
     * NOTI-003: Fetches notifications for the current user according to the filter.
     * <p>
     * filter = "unread" → only unread notifications
     * filter = "read"   → only read notifications
     * filter = "all"    → all notifications (default)
     * </p>
     */
    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(String filter) {
        // Get the currently authenticated user
        User currentUser = SecurityUtils.getCurrentUser();
        UUID userId = currentUser.getUserId();

        // Fetch based on filter param
        List<Notification> notifications = switch (filter.toLowerCase()) {
            case "unread" -> notificationRepository
                    .findByUser_UserIdAndIsReadOrderByCreatedAtDesc(userId, false);
            case "read" -> notificationRepository
                    .findByUser_UserIdAndIsReadOrderByCreatedAtDesc(userId, true);
            default -> notificationRepository
                    .findByUser_UserIdOrderByCreatedAtDesc(userId);
        };

        // Map entities to response DTOs
        return notifications.stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * NOTI-003: Returns the unread badge count for the current user.
     */
    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount() {
        User currentUser = SecurityUtils.getCurrentUser();
        long count = notificationRepository.countByUser_UserIdAndIsRead(currentUser.getUserId(), false);
        return UnreadCountResponse.builder().unreadCount(count).build();
    }

    // ─── Write Operations ─────────────────────────────────────────────────────

    /**
     * NOTI-003: Marks a single notification as read by setting isRead=true and recording readAt.
     * Validates that the notification belongs to the current user (prevents cross-user access).
     *
     * @throws NotificationNotFoundException if notification is not found or owned by another user
     */
    @Override
    @Transactional
    public void markAsRead(UUID notificationId) {
        User currentUser = SecurityUtils.getCurrentUser();

        // Find the notification — also enforces ownership check
        Notification notification = notificationRepository
                .findByNotificationIdAndUser_UserId(notificationId, currentUser.getUserId())
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        // Only update if currently unread to avoid unnecessary DB writes
        if (Boolean.FALSE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(OffsetDateTime.now());
            notificationRepository.save(notification);
            log.debug("Marked notification {} as read for user {}", notificationId, currentUser.getUserId());
        }
    }

    /**
     * NOTI-003: Marks all unread notifications of the current user as read.
     * Batch-updates isRead and readAt in a single transaction for efficiency.
     */
    @Override
    @Transactional
    public void markAllAsRead() {
        User currentUser = SecurityUtils.getCurrentUser();
        UUID userId = currentUser.getUserId();

        // Fetch only unread notifications — no point touching already-read ones
        List<Notification> unreadNotifications = notificationRepository
                .findByUser_UserIdAndIsReadOrderByCreatedAtDesc(userId, false);

        if (unreadNotifications.isEmpty()) {
            return; // Nothing to do
        }

        OffsetDateTime now = OffsetDateTime.now();
        unreadNotifications.forEach(n -> {
            n.setIsRead(true);
            n.setReadAt(now);
        });

        notificationRepository.saveAll(unreadNotifications);
        log.debug("Marked {} notifications as read for user {}", unreadNotifications.size(), userId);
    }

    // ─── Internal Creation ─────────────────────────────────────────────────────

    /**
     * NOTI-001: Internal method used by other services to dispatch a new in-app notification.
     * <p>
     * Called by ExamService, AppealService, GradingService, etc. when domain events occur.
     * </p>
     *
     * @param userId            recipient user UUID
     * @param title             short notification title
     * @param body              full message body
     * @param relatedEntityType deep-link type (EXAM, APPEAL, SUBMISSION, PAYMENT, GRADING_RESULT)
     * @param relatedEntityId   deep-link entity UUID (may be null)
     */
    @Override
    @Transactional
    public void createNotification(UUID userId, String title, String body,
                                   String relatedEntityType, UUID relatedEntityId) {
        // Build a User reference — only the ID is needed for the FK relationship
        User userRef = User.builder().userId(userId).build();

        Notification notification = Notification.builder()
                .user(userRef)
                .title(title)
                .body(body)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .isRead(false)
                .build();

        notificationRepository.save(notification);
        log.info("Dispatched notification '{}' to user {}", title, userId);
    }

    // ─── SCH-005 Cleanup ─────────────────────────────────────────────────────

    /**
     * SCH-005: Deletes read notifications older than 30 days.
     * Triggered weekly by {@code NotificationCleanupScheduler}.
     *
     * @return number of deleted records
     */
    @Override
    @Transactional
    public int cleanupOldNotifications() {
        // Calculate the cutoff: notifications created before this timestamp will be purged
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(30);
        int deleted = notificationRepository.deleteReadNotificationsOlderThan(cutoff);
        log.info("SCH-005: Deleted {} old read notifications (cutoff: {})", deleted, cutoff);
        return deleted;
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    /**
     * Maps a {@link Notification} entity to a {@link NotificationResponse} DTO.
     */
    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .notificationId(notification.getNotificationId())
                .title(notification.getTitle())
                .body(notification.getBody())
                .relatedEntityType(notification.getRelatedEntityType())
                .relatedEntityId(notification.getRelatedEntityId())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
