package agsfjope.backend.application.dtos.responses.staffdashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the Staff Dashboard overview panel.
 * <p>
 * Contains the four key summary metrics displayed in the top card row:
 * active exams, total submissions, graded submissions, and pending appeals.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffDashboardOverviewResponse {

    /** Number of exams currently in ONGOING status. */
    private long activeExams;

    /** Total number of submissions across all blocks. */
    private long totalSubmissions;

    /** Number of submissions that have been graded (status = GRADED). */
    private long gradedSubmissions;

    /** Number of appeals in PENDING status (awaiting staff assignment). */
    private long pendingAppeals;
}
