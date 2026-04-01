package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dashboardservices.DashboardService;
import agsfjope.backend.application.dtos.responses.dashboard.DashboardOverviewResponse;
import agsfjope.backend.application.dtos.responses.dashboard.RecentActivityResponse;
import agsfjope.backend.application.dtos.responses.dashboard.SystemActivityResponse;
import agsfjope.backend.application.dtos.responses.dashboard.SystemHealthResponse;
import agsfjope.backend.application.dtos.responses.dashboard.UserStatsByRoleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Admin Dashboard data endpoints.
 * <p>
 * All endpoints are restricted to the {@code SYSTEM_ADMIN} role and return
 * the standard {@code { success, message, data, errors }} response format.
 * Base path: {@code /api/admin/dashboard}
 * </p>
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    // ─── Overview ────────────────────────────────────────────────────────────

    /**
     * Returns the four top-level summary metrics displayed as cards on the dashboard:
     * <ul>
     *   <li>Total Users — all non-deleted users</li>
     *   <li>Active Exams — exams with status ONGOING</li>
     *   <li>Total Submissions — all student submissions</li>
     *   <li>Pending Appeals — appeals awaiting staff assignment</li>
     * </ul>
     *
     * @return 200 with {@link DashboardOverviewResponse}
     */
    @GetMapping("/overview")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        DashboardOverviewResponse data = dashboardService.getOverview(from, to);
        return ResponseEntity.ok(buildSuccess("Lấy thống kê tổng quan thành công", data));
    }

    // ─── User Stats ───────────────────────────────────────────────────────────

    /**
     * Returns user counts grouped by role for the donut chart
     * ("Thống kê người dùng theo Role").
     * <p>
     * Roles included: STUDENT, LECTURER, EXAM_STAFF, SYSTEM_ADMIN.
     * </p>
     *
     * @return 200 with {@link UserStatsByRoleResponse}
     */
    @GetMapping("/user-stats")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserStatsByRole(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UserStatsByRoleResponse data = dashboardService.getUserStatsByRole(from, to);
        return ResponseEntity.ok(buildSuccess("Lấy thống kê người dùng theo role thành công", data));
    }

    // ─── Recent Activities ────────────────────────────────────────────────────

    /**
     * Returns the most recent system activities for the "Recent Activities" table.
     * Each entry shows the user, action, entity type, IP address, and timestamp.
     *
     * @param limit maximum number of entries to return (default: 10, max practical: 50)
     * @return 200 with list of {@link RecentActivityResponse}
     */
    @GetMapping("/recent-activities")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> getRecentActivities(
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        List<RecentActivityResponse> data = dashboardService.getRecentActivities(limit, from, to);
        return ResponseEntity.ok(buildSuccess("Lấy hoạt động gần đây thành công", data));
    }

    // ─── System Health ────────────────────────────────────────────────────────

    /**
     * Returns real-time server resource usage for the "System Health" panel:
     * CPU usage (%), RAM usage (%), and disk usage (%) along with absolute values.
     * <p>
     * Metrics are read from JVM management beans and the local filesystem.
     * </p>
     *
     * @return 200 with {@link SystemHealthResponse}
     */
    @GetMapping("/system-health")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        SystemHealthResponse data = dashboardService.getSystemHealth();
        return ResponseEntity.ok(buildSuccess("Lấy thông tin hệ thống thành công", data));
    }

    // ─── System Activity ─────────────────────────────────────────────────────

    /**
     * Returns time-series activity data for the "Hoạt động hệ thống" line chart.
     * <ul>
     *   <li>{@code period=24h} — 24 hourly data points (default)</li>
     *   <li>{@code period=7d}  — 7 daily data points</li>
     * </ul>
     *
     * @param period time window: {@code "24h"} or {@code "7d"} (default: {@code "24h"})
     * @return 200 with {@link SystemActivityResponse}
     */
    @GetMapping("/system-activity")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> getSystemActivity(
            @RequestParam(name = "period", defaultValue = "24h") String period) {
        SystemActivityResponse data = dashboardService.getSystemActivity(period);
        return ResponseEntity.ok(buildSuccess("Lấy dữ liệu hoạt động hệ thống thành công", data));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Builds the standard success response envelope.
     *
     * @param message human-readable success message
     * @param data    response payload
     * @return {@code { success: true, message: "...", data: {...}, errors: null }}
     */
    private Map<String, Object> buildSuccess(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data",    data);
        response.put("errors",  null);
        return response;
    }
}
