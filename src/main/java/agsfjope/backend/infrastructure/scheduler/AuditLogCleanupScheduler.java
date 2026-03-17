package agsfjope.backend.infrastructure.scheduler;

import agsfjope.backend.application.auditlogservices.AuditLogService;
import agsfjope.backend.core.entities.SystemConfig;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for SCH-004: Auto Log Retention Cleanup.
 * <p>
 * Runs once per week (Sunday at 04:00 AM GMT+7 = 21:00 UTC Saturday)
 * to purge audit log entries older than the configured retention period.
 * </p>
 *
 * <p>Retention period is read from {@code SystemConfig} table with key
 * {@code log_retention_days}. If not configured, defaults to <strong>90 days</strong>.</p>
 *
 * <p>Requires {@code @EnableScheduling} to be active on the main application class.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogCleanupScheduler {

    private static final String CONFIG_KEY_RETENTION_DAYS = "log_retention_days";
    private static final int DEFAULT_RETENTION_DAYS = 90;

    private final AuditLogService auditLogService;
    private final SystemConfigRepository systemConfigRepository;

    /**
     * SCH-004: Weekly cleanup of old audit logs.
     * <p>
     * Cron breakdown: {@code 0 0 21 * * SAT}
     * <ul>
     *   <li>0  — at second 0</li>
     *   <li>0  — at minute 0</li>
     *   <li>21 — at hour 21 UTC (= 04:00 GMT+7 Sunday morning)</li>
     *   <li>*  — any day of month</li>
     *   <li>*  — any month</li>
     *   <li>SAT — every Saturday (21:00 UTC Saturday = 04:00 Sunday GMT+7)</li>
     * </ul>
     * </p>
     */
    @Scheduled(cron = "0 0 21 * * SAT")
    public void cleanupOldAuditLogs() {
        log.info("SCH-004: Starting scheduled cleanup of old audit logs...");
        try {
            int retentionDays = getRetentionDays();
            int deletedCount = auditLogService.cleanupOldLogs(retentionDays);
            log.info("SCH-004: Cleanup completed — {} audit logs deleted (retention: {} days).",
                    deletedCount, retentionDays);
        } catch (Exception e) {
            // Log the error but never re-throw — scheduler must survive failures
            log.error("SCH-004: Audit log cleanup failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Reads the retention period from SystemConfig.
     * Falls back to {@value #DEFAULT_RETENTION_DAYS} days if not configured or invalid.
     */
    private int getRetentionDays() {
        try {
            return systemConfigRepository.findByConfigKey(CONFIG_KEY_RETENTION_DAYS)
                    .map(SystemConfig::getConfigValue)
                    .map(Integer::parseInt)
                    .orElse(DEFAULT_RETENTION_DAYS);
        } catch (NumberFormatException e) {
            log.warn("SCH-004: Invalid log_retention_days config value, using default {}",
                    DEFAULT_RETENTION_DAYS);
            return DEFAULT_RETENTION_DAYS;
        }
    }
}
