package agsfjope.backend.application.dtos.responses.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the Admin Dashboard overview panel.
 * <p>
 * Contains the four key summary metrics displayed in the top card row:
 * total users, active exams, total submissions, and pending appeals.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardOverviewResponse {

    /** Total number of non-deleted users in the system. */
    private long totalUsers;

    /** Number of exams currently in ONGOING status. */
    private long activeExams;

    /** Total number of submissions across all blocks. */
    private long totalSubmissions;

    /** Number of appeals in PENDING status (awaiting staff assignment). */
    private long pendingAppeals;
}
