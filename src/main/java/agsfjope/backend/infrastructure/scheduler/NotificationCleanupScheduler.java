package agsfjope.backend.infrastructure.scheduler;

import agsfjope.backend.application.notificationservices.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for SCH-005: Auto Clear Old Notifications.
 * <p>
 * Runs once per week (Sunday at 03:00 AM GMT+7) to purge in-app notifications
 * that have already been read and are older than 30 days.
 * This helps reduce database size without affecting the user experience,
 * since old read notifications are no longer relevant.
 * </p>
 *
 * <p>Requires {@code @EnableScheduling} to be active — enabled in the main application class.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private final NotificationService notificationService;

    /**
     * SCH-005: Weekly cleanup of read notifications older than 30 days.
     * <p>
     * Cron breakdown: {@code 0 0 20 * * SUN}
     * <ul>
     *   <li>0  — at second 0</li>
     *   <li>0  — at minute 0</li>
     *   <li>20 — at hour 20 UTC (= 03:00 GMT+7 on Monday morning)</li>
     *   <li>*  — any day of month</li>
     *   <li>*  — any month</li>
     *   <li>SUN — every Sunday</li>
     * </ul>
     * </p>
     */
    @Scheduled(cron = "0 0 20 * * SUN")
    public void cleanupOldNotifications() {
        log.info("SCH-005: Starting scheduled cleanup of old read notifications...");
        try {
            int deletedCount = notificationService.cleanupOldNotifications();
            log.info("SCH-005: Cleanup completed — {} notifications deleted.", deletedCount);
        } catch (Exception e) {
            // Log the error but do not re-throw — scheduler must survive failures
            log.error("SCH-005: Cleanup failed with error: {}", e.getMessage(), e);
        }
    }
}
