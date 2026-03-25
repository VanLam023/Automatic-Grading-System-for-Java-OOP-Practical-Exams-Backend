package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.responses.staffdashboard.GradeDistributionResponse;
import agsfjope.backend.application.dtos.responses.staffdashboard.PendingAppealResponse;
import agsfjope.backend.application.dtos.responses.staffdashboard.RecentExamResponse;
import agsfjope.backend.application.dtos.responses.staffdashboard.StaffDashboardOverviewResponse;
import agsfjope.backend.application.staffdashboardservices.StaffDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Exam Staff Dashboard data endpoints.
 * <p>
 * All endpoints are restricted to the {@code EXAM_STAFF} role and return
 * the standard {@code { success, message, data, errors }} response format.
 * Base path: {@code /api/staff/dashboard}
 * </p>
 */
@RestController
@RequestMapping("/api/staff/dashboard")
@RequiredArgsConstructor
public class StaffDashboardController {

    private final StaffDashboardService staffDashboardService;

    // ─── Overview ────────────────────────────────────────────────────────────

    /**
     * Returns the four top-level summary metrics displayed as cards on the staff dashboard:
     * <ul>
     *   <li>Active Exams — exams with status ONGOING</li>
     *   <li>Total Submissions — all student submissions</li>
     *   <li>Graded Submissions — submissions with status GRADED</li>
     *   <li>Pending Appeals — appeals awaiting staff assignment</li>
     * </ul>
     *
     * @param semester optional semester filter (e.g. "Summer 2024")
     * @return 200 with {@link StaffDashboardOverviewResponse}
     */
    @GetMapping("/overview")
    @PreAuthorize("hasRole('EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> getOverview(
            @RequestParam(name = "semester", required = false) String semester) {
        StaffDashboardOverviewResponse data = staffDashboardService.getOverview(semester);
        return ResponseEntity.ok(buildSuccess("Lấy thống kê tổng quan thành công", data));
    }

    // ─── Recent Exams ────────────────────────────────────────────────────────

    /**
     * Returns the most recent exams for the "Kỳ thi gần đây" table.
     * Each entry shows the exam name, semester, and current status.
     *
     * @param limit    maximum number of exams to return (default: 5)
     * @param semester optional semester filter
     * @return 200 with list of {@link RecentExamResponse}
     */
    @GetMapping("/recent-exams")
    @PreAuthorize("hasRole('EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> getRecentExams(
            @RequestParam(name = "limit", defaultValue = "5") int limit,
            @RequestParam(name = "semester", required = false) String semester) {
        List<RecentExamResponse> data = staffDashboardService.getRecentExams(limit, semester);
        return ResponseEntity.ok(buildSuccess("Lấy danh sách kỳ thi gần đây thành công", data));
    }

    // ─── Grade Distribution ──────────────────────────────────────────────────

    /**
     * Returns score distribution data for the "Phân bố điểm" bar chart.
     * Scores are bucketed into 5 ranges: 0-4, 4-6, 6-8, 8-9, 9-10.
     *
     * @param semester optional semester filter
     * @return 200 with {@link GradeDistributionResponse}
     */
    @GetMapping("/grade-distribution")
    @PreAuthorize("hasRole('EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> getGradeDistribution(
            @RequestParam(name = "semester", required = false) String semester) {
        GradeDistributionResponse data = staffDashboardService.getGradeDistribution(semester);
        return ResponseEntity.ok(buildSuccess("Lấy phân bố điểm thành công", data));
    }

    // ─── Pending Appeals ─────────────────────────────────────────────────────

    /**
     * Returns appeals pending staff review for the "Đơn phúc khảo cần xử lý" table.
     * Includes appeals with PENDING and PROCESSING statuses.
     *
     * @param limit    maximum number of appeals to return (default: 10)
     * @param semester optional semester filter
     * @return 200 with list of {@link PendingAppealResponse}
     */
    @GetMapping("/pending-appeals")
    @PreAuthorize("hasRole('EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> getPendingAppeals(
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "semester", required = false) String semester) {
        List<PendingAppealResponse> data = staffDashboardService.getPendingAppeals(limit, semester);
        return ResponseEntity.ok(buildSuccess("Lấy danh sách phúc khảo cần xử lý thành công", data));
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
