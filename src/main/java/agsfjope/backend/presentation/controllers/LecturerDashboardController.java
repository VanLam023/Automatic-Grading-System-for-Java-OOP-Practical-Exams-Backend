package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.responses.lecturerdashboard.AssignedAppealResponse;
import agsfjope.backend.application.dtos.responses.lecturerdashboard.LecturerDashboardOverviewResponse;
import agsfjope.backend.application.dtos.responses.lecturerdashboard.ReviewStatsResponse;
import agsfjope.backend.application.dtos.responses.lecturerdashboard.UpcomingDeadlineResponse;
import agsfjope.backend.application.lecturerdashboardservices.LecturerDashboardService;
import agsfjope.backend.infrastructure.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for Lecturer Dashboard data endpoints.
 * <p>
 * All endpoints are restricted to the {@code LECTURER} role.
 * Data is automatically scoped to the currently authenticated lecturer
 * via their JWT token — no user ID parameter is required from the client.
 * Base path: {@code /api/lecturer/dashboard}
 * </p>
 */
@RestController
@RequestMapping("/api/lecturer/dashboard")
@RequiredArgsConstructor
public class LecturerDashboardController {

    private final LecturerDashboardService lecturerDashboardService;

    // ─── Overview ────────────────────────────────────────────────────────────

    /**
     * Returns the three summary cards for the logged-in lecturer:
     * <ul>
     *   <li>Assigned Appeals — appeals with status PROCESSING assigned to this lecturer</li>
     *   <li>Completed Reviews — appeals with status COMPLETED, APPROVED, or DENIED</li>
     *   <li>Overdue Appeals — PROCESSING appeals whose deadline has already passed</li>
     * </ul>
     *
     * @param authentication injected by Spring Security
     * @return 200 with {@link LecturerDashboardOverviewResponse}
     */
    @GetMapping("/overview")
    @PreAuthorize("hasRole('LECTURER')")
    public ResponseEntity<Map<String, Object>> getOverview(Authentication authentication) {
        UUID lecturerId = extractUserId(authentication);
        LecturerDashboardOverviewResponse data = lecturerDashboardService.getOverview(lecturerId);
        return ResponseEntity.ok(buildSuccess("Lấy thống kê tổng quan thành công", data));
    }

    // ─── Assigned Appeals ────────────────────────────────────────────────────

    /**
     * Returns appeals assigned to the logged-in lecturer for the table view.
     * Ordered by assignment date, newest first.
     *
     * @param limit          maximum number of rows (default: 10)
     * @param authentication injected by Spring Security
     * @return 200 with list of {@link AssignedAppealResponse}
     */
    @GetMapping("/assigned-appeals")
    @PreAuthorize("hasRole('LECTURER')")
    public ResponseEntity<Map<String, Object>> getAssignedAppeals(
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "status", required = false) String status,
            Authentication authentication) {
        UUID lecturerId = extractUserId(authentication);
        List<AssignedAppealResponse> data = lecturerDashboardService.getAssignedAppeals(lecturerId, limit, status);
        return ResponseEntity.ok(buildSuccess("Lấy danh sách phúc khảo được phân công thành công", data));
    }

    // ─── Upcoming Deadlines ──────────────────────────────────────────────────

    /**
     * Returns PROCESSING appeals with upcoming deadlines for the "Deadline sắp tới" section.
     * Each item includes an urgency label: CẦN XỬ LÝ NGAY / TRONG 2 NGÀY TỚI / SẮP TỚI.
     *
     * @param limit          maximum number of items (default: 5)
     * @param authentication injected by Spring Security
     * @return 200 with list of {@link UpcomingDeadlineResponse}
     */
    @GetMapping("/upcoming-deadlines")
    @PreAuthorize("hasRole('LECTURER')")
    public ResponseEntity<Map<String, Object>> getUpcomingDeadlines(
            @RequestParam(name = "limit", defaultValue = "5") int limit,
            Authentication authentication) {
        UUID lecturerId = extractUserId(authentication);
        List<UpcomingDeadlineResponse> data = lecturerDashboardService.getUpcomingDeadlines(lecturerId, limit);
        return ResponseEntity.ok(buildSuccess("Lấy danh sách deadline sắp tới thành công", data));
    }

    // ─── Review Stats ─────────────────────────────────────────────────────────

    /**
     * Returns review statistics for the "Thống kê" donut chart:
     * total reviews completed, approved count/percentage, denied count/percentage.
     *
     * @param authentication injected by Spring Security
     * @return 200 with {@link ReviewStatsResponse}
     */
    @GetMapping("/review-stats")
    @PreAuthorize("hasRole('LECTURER')")
    public ResponseEntity<Map<String, Object>> getReviewStats(Authentication authentication) {
        UUID lecturerId = extractUserId(authentication);
        ReviewStatsResponse data = lecturerDashboardService.getReviewStats(lecturerId);
        return ResponseEntity.ok(buildSuccess("Lấy thống kê đánh giá thành công", data));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Extracts the current lecturer's UUID from the Spring Security authentication token.
     *
     * @param authentication the current authentication context
     * @return lecturer's user UUID
     */
    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("Không tìm thấy thông tin xác thực. Vui lòng đăng nhập lại.");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getUser().getUserId();
        }
        throw new IllegalStateException("Không thể xác định danh tính người dùng từ token.");
    }

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
