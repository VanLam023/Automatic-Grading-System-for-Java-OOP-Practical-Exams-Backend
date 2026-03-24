package agsfjope.backend.application.dashboardservices;

import agsfjope.backend.application.dtos.responses.dashboard.DashboardOverviewResponse;
import agsfjope.backend.application.dtos.responses.dashboard.RecentActivityResponse;
import agsfjope.backend.application.dtos.responses.dashboard.SystemActivityResponse;
import agsfjope.backend.application.dtos.responses.dashboard.SystemHealthResponse;
import agsfjope.backend.application.dtos.responses.dashboard.UserStatsByRoleResponse;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service interface for Admin Dashboard data aggregation.
 * <p>
 * All methods are intended to be called only by users with the
 * {@code SYSTEM_ADMIN} role, as enforced at the presentation layer.
 * </p>
 */
public interface DashboardService {

    /**
     * Returns the four top-level summary metrics for the dashboard overview cards:
     * total users, active (ONGOING) exams, total submissions, and pending appeals.
     * When {@code from} and {@code to} are both non-null, only records created
     * within that date range are counted.
     *
     * @param from optional start of date range (inclusive); null means no lower bound
     * @param to   optional end of date range (inclusive); null means no upper bound
     * @return dashboard overview metrics
     */
    DashboardOverviewResponse getOverview(OffsetDateTime from, OffsetDateTime to);

    /**
     * Returns a breakdown of non-deleted users grouped by role.
     * Used to render the "Thống kê người dùng theo Role" donut chart.
     * When {@code from} and {@code to} are both non-null, only users registered
     * within that date range are counted.
     *
     * @param from optional start of date range (inclusive); null means no lower bound
     * @param to   optional end of date range (inclusive); null means no upper bound
     * @return total user count and per-role counts
     */
    UserStatsByRoleResponse getUserStatsByRole(OffsetDateTime from, OffsetDateTime to);

    /**
     * Returns the most recent audit log entries for the "Recent Activities" table.
     * When {@code from} and {@code to} are both non-null, only entries created
     * within that date range are returned.
     *
     * @param limit maximum number of entries to return (typically 10)
     * @param from  optional start of date range (inclusive); null means no lower bound
     * @param to    optional end of date range (inclusive); null means no upper bound
     * @return ordered list of recent activities, newest first
     */
    List<RecentActivityResponse> getRecentActivities(int limit, OffsetDateTime from, OffsetDateTime to);

    /**
     * Returns real-time server resource usage for the "System Health" panel.
     * Metrics are obtained from JVM management beans and the filesystem.
     *
     * @return current CPU, memory, and disk usage statistics
     */
    SystemHealthResponse getSystemHealth();

    /**
     * Returns time-series activity data for the "Hoạt động hệ thống" line chart.
     * Groups audit log entries into hourly buckets for "24h" or daily buckets for "7d".
     *
     * @param period the time window — either {@code "24h"} or {@code "7d"}
     * @return ordered list of activity data points from oldest to newest
     */
    SystemActivityResponse getSystemActivity(String period);
}
