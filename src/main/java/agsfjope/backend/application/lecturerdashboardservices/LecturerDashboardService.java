package agsfjope.backend.application.lecturerdashboardservices;

import agsfjope.backend.application.dtos.responses.lecturerdashboard.AssignedAppealResponse;
import agsfjope.backend.application.dtos.responses.lecturerdashboard.LecturerDashboardOverviewResponse;
import agsfjope.backend.application.dtos.responses.lecturerdashboard.ReviewStatsResponse;
import agsfjope.backend.application.dtos.responses.lecturerdashboard.UpcomingDeadlineResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for Lecturer Dashboard data aggregation.
 * <p>
 * All data is scoped to the currently logged-in lecturer's assigned appeals.
 * The {@code lecturerId} is resolved by the controller from the JWT token.
 * </p>
 */
public interface LecturerDashboardService {

    /**
     * Returns the three summary metrics for the lecturer dashboard overview cards.
     *
     * @param lecturerId the UUID of the currently logged-in lecturer
     * @return overview metrics (assignedAppeals, completedReviews, overdueAppeals)
     */
    LecturerDashboardOverviewResponse getOverview(UUID lecturerId);

    /**
     * Returns appeals assigned to this lecturer for the "Đơn phúc khảo được phân công" table.
     *
     * @param lecturerId the UUID of the currently logged-in lecturer
     * @param limit      maximum number of rows to return
     * @param status     optional status filter (e.g. "PROCESSING"); null means all statuses
     * @return ordered list of assigned appeals, newest first
     */
    List<AssignedAppealResponse> getAssignedAppeals(UUID lecturerId, int limit, String status);

    /**
     * Returns upcoming deadlines for the "Deadline sắp tới" section.
     * Only PROCESSING appeals are returned, sorted by deadline ascending.
     *
     * @param lecturerId the UUID of the currently logged-in lecturer
     * @param limit      maximum number of items to return
     * @return list of deadline items with urgency labels
     */
    List<UpcomingDeadlineResponse> getUpcomingDeadlines(UUID lecturerId, int limit);

    /**
     * Returns review statistics for the "Thống kê" donut chart.
     *
     * @param lecturerId the UUID of the currently logged-in lecturer
     * @return approved/denied counts and percentages
     */
    ReviewStatsResponse getReviewStats(UUID lecturerId);
}
