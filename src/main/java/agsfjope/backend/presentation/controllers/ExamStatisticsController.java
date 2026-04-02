package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.responses.statistics.BlockStatisticsResponse;
import agsfjope.backend.application.examstatisticsservices.ExamStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller cho Exam Statistics (PROC-006).
 *
 * <p>Cung cấp endpoint thống kê tổng hợp cho một block trong kỳ thi.
 * Endpoint được đặt dưới cấu trúc URL quen thuộc:
 * {@code /api/exams/{examId}/blocks/{blockId}/statistics}</p>
 *
 * <h3>Authorization:</h3>
 * <ul>
 *   <li>Chỉ EXAM_STAFF và SYSTEM_ADMIN mới được truy cập</li>
 * </ul>
 *
 * <h3>Response format:</h3>
 * <pre>
 * {
 *   "success": true,
 *   "message": "...",
 *   "data": { ... BlockStatisticsResponse ... },
 *   "errors": null
 * }
 * </pre>
 */
@RestController
@RequiredArgsConstructor
public class ExamStatisticsController {

    private final ExamStatisticsService examStatisticsService;

    private static final String STAFF_ROLES =
            "hasAnyAuthority('EXAM_STAFF','ROLE_EXAM_STAFF','SYSTEM_ADMIN','ROLE_SYSTEM_ADMIN')";

    /**
     * Returns comprehensive statistics for a specific block within an exam.
     *
     * <p>Includes 4 groups of data:
     * <ol>
     *   <li>Submission Overview: total/graded counts</li>
     *   <li>Score Analysis: avg/max/min, pass/fail rates, histogram</li>
     *   <li>AI OOP Analysis: violation counts per criterion (threshold: score &lt; 2)</li>
     *   <li>Appeal &amp; Financial: appeal status breakdown, revenue</li>
     * </ol>
     * </p>
     *
     * @param examId  the exam UUID
     * @param blockId the block UUID (must belong to the given exam)
     * @return block-level statistics
     */
    @GetMapping("/api/exams/{examId}/blocks/{blockId}/statistics")
    @PreAuthorize(STAFF_ROLES)
    public ResponseEntity<Map<String, Object>> getBlockStatistics(
            @PathVariable UUID examId,
            @PathVariable UUID blockId
    ) {
        BlockStatisticsResponse stats = examStatisticsService.getBlockStatistics(examId, blockId);
        return ResponseEntity.ok(buildSuccessResponse(
                "Thống kê block thành công.", stats));
    }

    // ─── Helper ─────────────────────────────────────────────────────────────

    private Map<String, Object> buildSuccessResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        response.put("errors", null);
        return response;
    }
}
