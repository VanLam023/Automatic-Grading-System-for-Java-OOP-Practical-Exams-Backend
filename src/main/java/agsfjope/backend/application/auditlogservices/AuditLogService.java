package agsfjope.backend.application.auditlogservices;

import agsfjope.backend.application.dtos.responses.auditlog.AuditLogResponse;
import agsfjope.backend.core.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service interface for Audit Log operations (TRG-008, BR-48, SCH-004).
 * <p>
 * Covers three concerns:
 * <ul>
 *   <li><strong>Read</strong>: List and detail views for System Admin</li>
 *   <li><strong>Write</strong>: Recording audit entries (called by AOP aspect or AuditLogHelper)</li>
 *   <li><strong>Cleanup</strong>: SCH-004 scheduled purge of old logs</li>
 * </ul>
 * </p>
 */
public interface AuditLogService {

    /**
     * Returns a paginated, filterable list of audit log entries.
     * System Admin only — used in the Audit Log admin page.
     *
     * @param action     filter by action type (optional)
     * @param entityType filter by entity type (optional)
     * @param userId     filter by performing user (optional)
     * @param from       start of date range (optional)
     * @param to         end of date range (optional)
     * @param pageable   pagination and sorting parameters
     * @return paginated list of audit log DTOs
     */
    Page<AuditLogResponse> getAuditLogs(AuditAction action, String entityType,
                                         UUID userId, OffsetDateTime from,
                                         OffsetDateTime to, Pageable pageable);

    /**
     * Returns the full details of a single audit log entry.
     * Includes old/new values, IP address, user agent, correlation ID.
     *
     * @param auditLogId the audit log UUID
     * @return detailed audit log DTO
     */
    AuditLogResponse getAuditLogById(UUID auditLogId);

    /**
     * TRG-008: Records a new audit log entry.
     * <p>
     * Called internally by {@code AuditLogAspect} (AOP) or {@code AuditLogHelper} (manual).
     * Persists the action with user, entity, old/new values, and client metadata.
     * </p>
     *
     * @param userId     UUID of the user performing the action (null for system actions)
     * @param action     the action type
     * @param entityType type of entity being acted upon
     * @param entityId   UUID of the affected entity (may be null)
     * @param oldValues  JSON string of previous state (null for CREATE)
     * @param newValues  JSON string of new state (null for DELETE)
     * @param ipAddress  client IP address
     * @param userAgent  client user agent string
     */
    void logAction(UUID userId, AuditAction action, String entityType,
                   UUID entityId, String oldValues, String newValues,
                   String ipAddress, String userAgent);

    /**
     * SCH-004: Deletes audit logs older than the given retention period.
     * Called weekly by {@code AuditLogCleanupScheduler}.
     *
     * @param retentionDays number of days to retain logs
     * @return number of deleted records
     */
    int cleanupOldLogs(int retentionDays);
}
