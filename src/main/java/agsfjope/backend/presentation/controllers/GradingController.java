package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.requests.grading.TriggerGradingRequest;
import agsfjope.backend.application.dtos.responses.grading.GradingProgressResponse;
import agsfjope.backend.application.dtos.responses.grading.GradingResultResponse;
import agsfjope.backend.application.gradingservices.GradingQueryService;
import agsfjope.backend.application.gradingservices.GradingService;
import agsfjope.backend.infrastructure.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Grading operations.
 *
 * <p>All grading endpoints are nested under an exam block:
 * {@code /api/exams/{examId}/blocks/{blockId}/grading/...}</p>
 *
 * <h3>Endpoints overview:</h3>
 * <pre>
 * POST   /grading/trigger                   — Chấm tất cả (submissionIds = null)
 *                                             or Chấm các bài đã chọn (submissionIds = [...])
 * POST   /grading/trigger/{submissionId}    — Chấm 1 bài cụ thể
 * GET    /grading/progress                  — Xem tiến độ chấm
 * GET    /grading/results                   — Danh sách kết quả toàn block
 * GET    /api/exams/{examId}/blocks/{blockId}/grading/my-result — Kết quả của mình (STUDENT)
 * GET    /api/submissions/{submissionId}/grading-result          — Kết quả chi tiết (STUDENT + STAFF)
 * </pre>
 *
 * <h3>Authorization:</h3>
 * <ul>
 *   <li>Trigger + List results: {@code EXAM_STAFF}, {@code SYSTEM_ADMIN}</li>
 *   <li>My result + Submission detail: {@code STUDENT}, {@code EXAM_STAFF}, {@code SYSTEM_ADMIN}</li>
 *   <li>Progress: {@code EXAM_STAFF}, {@code SYSTEM_ADMIN}</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class GradingController {

    private final GradingService      gradingService;
    private final GradingQueryService gradingQueryService;

    private static final String STAFF_ROLES =
            "hasAnyAuthority('EXAM_STAFF','ROLE_EXAM_STAFF','SYSTEM_ADMIN','ROLE_SYSTEM_ADMIN')";
    private static final String ALL_ROLES =
            "hasAnyAuthority('STUDENT','ROLE_STUDENT','EXAM_STAFF','ROLE_EXAM_STAFF','SYSTEM_ADMIN','ROLE_SYSTEM_ADMIN')";

    // ─── TRIGGER: Grade all or selected ──────────────────────────────────────

    /**
     * Trigger grading for all SUBMITTED submissions in a block,
     * or for a specific selected list.
     *
     * <p>Body: {@code { "blockId": "...", "submissionIds": null|[...] }}</p>
     * <ul>
     *   <li>{@code submissionIds = null}  → grade ALL pending submissions</li>
     *   <li>{@code submissionIds = [id1, id2]} → grade only selected</li>
     * </ul>
     */
    @PostMapping("/api/exams/{examId}/blocks/{blockId}/grading/trigger")
    @PreAuthorize(STAFF_ROLES)
    public ResponseEntity<Map<String, Object>> triggerGrading(
            @PathVariable UUID examId,
            @PathVariable UUID blockId,
            @Valid @RequestBody TriggerGradingRequest request,
            Authentication authentication
    ) {
        request.setBlockId(blockId);
        gradingService.triggerGrading(blockId, request, extractUser(authentication));

        boolean isAll = request.getSubmissionIds() == null;
        String msg = isAll
                ? "Đã bắt đầu chấm tất cả bài nộp trong block. Theo dõi tiến độ tại /grading/progress"
                : "Đã bắt đầu chấm " + request.getSubmissionIds().size() + " bài nộp đã chọn.";

        return ResponseEntity.accepted().body(buildSuccessResponse(msg, null));
    }

    // ─── TRIGGER: Single submission ───────────────────────────────────────────

    /**
     * Trigger grading for a single submission.
     *
     * <p>Shorthand for {@code POST /grading/trigger} with
     * {@code submissionIds = [submissionId]}.</p>
     */
    @PostMapping("/api/exams/{examId}/blocks/{blockId}/grading/trigger/{submissionId}")
    @PreAuthorize(STAFF_ROLES)
    public ResponseEntity<Map<String, Object>> triggerSingleGrading(
            @PathVariable UUID examId,
            @PathVariable UUID blockId,
            @PathVariable UUID submissionId,
            Authentication authentication
    ) {
        TriggerGradingRequest request = new TriggerGradingRequest();
        request.setBlockId(blockId);
        request.setSubmissionIds(List.of(submissionId));

        gradingService.triggerGrading(blockId, request, extractUser(authentication));

        return ResponseEntity.accepted().body(
                buildSuccessResponse("Đã bắt đầu chấm bài nộp " + submissionId, null));
    }

    // ─── STOP ─────────────────────────────────────────────────────────────────

    /**
     * Sends a stop signal to the grading loop for this block.
     * Grading will stop cooperatively after the current answer (question) finishes.
     *
     * <p>Submissions that were already fully graded keep their GRADED status.
     * Submissions that were mid-grading (or not yet started) will be reset to SUBMITTED
     * so they can be re-graded later.</p>
     */
    @PostMapping("/api/exams/{examId}/blocks/{blockId}/grading/stop")
    @PreAuthorize(STAFF_ROLES)
    public ResponseEntity<Map<String, Object>> stopGrading(
            @PathVariable UUID examId,
            @PathVariable UUID blockId
    ) {
        gradingService.stopGrading(blockId);
        return ResponseEntity.ok(buildSuccessResponse(
                "Yêu cầu dừng chấm bài đã được ghi nhận. Quá trình sẽ dừng sau khi hoàn thành câu hiện tại.",
                null));
    }

    // ─── PROGRESS ─────────────────────────────────────────────────────────────

    /**
     * Returns current grading progress for the block.
     * Poll this endpoint to show grading progress to the staff.
     */
    @GetMapping("/api/exams/{examId}/blocks/{blockId}/grading/progress")
    @PreAuthorize(STAFF_ROLES)
    public ResponseEntity<Map<String, Object>> getProgress(
            @PathVariable UUID examId,
            @PathVariable UUID blockId
    ) {
        GradingProgressResponse progress = gradingService.getProgress(blockId);
        return ResponseEntity.ok(buildSuccessResponse("Tiến độ chấm bài.", progress));
    }

    // ─── LIST RESULTS (Staff) ─────────────────────────────────────────────────

    /**
     * Lists all grading results for the block.
     * Used by Staff to see all student scores.
     */
    @GetMapping("/api/exams/{examId}/blocks/{blockId}/grading/results")
    @PreAuthorize(STAFF_ROLES)
    public ResponseEntity<Map<String, Object>> listResults(
            @PathVariable UUID examId,
            @PathVariable UUID blockId
    ) {
        List<GradingResultResponse> results = gradingQueryService.getBlockResults(blockId);
        return ResponseEntity.ok(buildSuccessResponse(
                "Danh sách kết quả chấm: " + results.size() + " bài.", results));
    }

    // ─── MY RESULT (Student) ──────────────────────────────────────────────────

    /**
     * Returns the grading result for the authenticated student's own submission.
     */
    @GetMapping("/api/exams/{examId}/blocks/{blockId}/grading/my-result")
    @PreAuthorize(ALL_ROLES)
    public ResponseEntity<Map<String, Object>> getMyResult(
            @PathVariable UUID examId,
            @PathVariable UUID blockId,
            Authentication authentication
    ) {
        UUID studentId = extractUserId(authentication);
        GradingResultResponse result = gradingQueryService.getStudentResult(blockId, studentId);
        return ResponseEntity.ok(buildSuccessResponse("Kết quả chấm bài của bạn.", result));
    }

    // ─── SUBMISSION RESULT DETAIL ─────────────────────────────────────────────

    /**
     * Returns full grading detail for a specific submission.
     * Staff can view any submission; students can only view their own.
     */
    @GetMapping("/api/submissions/{submissionId}/grading-result")
    @PreAuthorize(ALL_ROLES)
    public ResponseEntity<Map<String, Object>> getSubmissionResult(
            @PathVariable UUID submissionId,
            Authentication authentication
    ) {
        UUID requesterId = extractUserId(authentication);
        GradingResultResponse result = gradingQueryService.getSubmissionResult(submissionId, requesterId);
        return ResponseEntity.ok(buildSuccessResponse("Kết quả chấm bài chi tiết.", result));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private agsfjope.backend.core.entities.User extractUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("Không tìm thấy thông tin xác thực. Vui lòng đăng nhập lại.");
        }
        if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUser();
        }
        throw new IllegalStateException("Không thể xác định danh tính người dùng từ token.");
    }

    private UUID extractUserId(Authentication authentication) {
        return extractUser(authentication).getUserId();
    }

    private Map<String, Object> buildSuccessResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        response.put("errors", null);
        return response;
    }
}
