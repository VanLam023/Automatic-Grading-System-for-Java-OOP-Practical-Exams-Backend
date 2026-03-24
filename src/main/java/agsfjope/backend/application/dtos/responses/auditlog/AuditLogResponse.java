package agsfjope.backend.application.dtos.responses.auditlog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO representing an audit log entry returned to System Admin.
 * <p>
 * Contains the full audit trail information: who performed the action,
 * what was changed (entity + old/new values), and client details (IP, userAgent).
 * The {@code oldValues} and {@code newValues} fields contain JSON strings
 * that the frontend can render in a diff viewer.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponse {

    /** Unique identifier of the audit log entry. */
    private UUID auditLogId;

    /** Username of the user who performed the action. */
    private String username;

    /** Role of the user at the time of the action (e.g. SYSTEM_ADMIN, STUDENT). */
    private String role;

    /** The action performed (CREATE, UPDATE, DELETE, LOGIN, etc.). */
    private String action;

    /** Type of entity affected (EXAM, SUBMISSION, APPEAL, CONFIG, etc.). */
    private String entityType;

    /** UUID of the affected entity (may be null for LOGIN/LOGOUT). */
    private UUID entityId;

    /** JSON string of the entity's state BEFORE the action (null for CREATE). */
    private String oldValues;

    /** JSON string of the entity's state AFTER the action (null for DELETE). */
    private String newValues;

    /** Client IP address that initiated the request. */
    private String ipAddress;

    /** Browser/client user agent string. */
    private String userAgent;

    /** Correlation ID for tracing related actions across a single request. */
    private UUID correlationId;

    /** Timestamp when the action was recorded. */
    private OffsetDateTime createdAt;
}
