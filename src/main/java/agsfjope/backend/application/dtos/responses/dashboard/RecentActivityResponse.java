package agsfjope.backend.application.dtos.responses.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Response DTO representing a single entry in the "Recent Activities" table
 * on the Admin Dashboard.
 * <p>
 * Each entry is derived from an {@code AuditLog} record and shows who did what,
 * when, and from which IP address.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentActivityResponse {

    /** Username of the user who performed the action. */
    private String username;

    /** Full name of the user who performed the action. */
    private String fullName;

    /** The audit action performed (e.g. "Updated AI Prompt", "Created New Exam"). */
    private String action;

    /** Entity type affected by this action (e.g. "EXAM", "SUBMISSION"). */
    private String entityType;

    /** Client IP address that initiated the request. */
    private String ipAddress;

    /** Timestamp when the action was recorded. */
    private OffsetDateTime createdAt;
}
