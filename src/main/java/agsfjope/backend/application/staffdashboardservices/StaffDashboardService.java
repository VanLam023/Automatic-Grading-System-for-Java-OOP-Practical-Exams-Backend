package agsfjope.backend.application.staffdashboardservices;

import agsfjope.backend.application.dtos.responses.staffdashboard.GradeDistributionResponse;
import agsfjope.backend.application.dtos.responses.staffdashboard.PendingAppealResponse;
import agsfjope.backend.application.dtos.responses.staffdashboard.RecentExamResponse;
import agsfjope.backend.application.dtos.responses.staffdashboard.StaffDashboardOverviewResponse;

import java.util.List;

/**
 * Service interface for Exam Staff Dashboard data aggregation.
 * <p>
 * All methods are intended to be called only by users with the
 * {@code EXAM_STAFF} role, as enforced at the presentation layer.
 * Every method accepts an optional {@code semester} parameter to filter data.
 * </p>
 */
public interface StaffDashboardService {

    /**
     * Returns the four summary metrics for the staff dashboard overview cards:
     * active exams, total submissions, graded submissions, and pending appeals.
     *
     * @param semester optional semester code to filter by; null means all semesters
     * @return staff dashboard overview metrics
     */
    StaffDashboardOverviewResponse getOverview(String semester);

    /**
     * Returns the most recent exams for the "Kỳ thi gần đây" table.
     *
     * @param limit    maximum number of exams to return
     * @param semester optional semester code to filter by; null means all semesters
     * @return ordered list of recent exams, newest first
     */
    List<RecentExamResponse> getRecentExams(int limit, String semester);

    /**
     * Returns score distribution data for the "Phân bố điểm" bar chart.
     * Buckets: 0-4, 4-6, 6-8, 8-9, 9-10.
     *
     * @param semester optional semester code to filter by; null means all semesters
     * @return grade distribution breakdown
     */
    GradeDistributionResponse getGradeDistribution(String semester);

    /**
     * Returns appeals pending staff review for the "Đơn phúc khảo cần xử lý" table.
     * Includes appeals with PENDING and PROCESSING statuses.
     *
     * @param limit    maximum number of appeals to return
     * @param semester optional semester code to filter by; null means all semesters
     * @return ordered list of pending appeals, newest first
     */
    List<PendingAppealResponse> getPendingAppeals(int limit, String semester);
}
