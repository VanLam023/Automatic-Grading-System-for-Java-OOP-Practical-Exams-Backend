package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.appealservices.StaffAppealService;
import agsfjope.backend.application.dtos.requests.appeal.AssignAppealRequest;
import agsfjope.backend.infrastructure.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller xử lý Appeal Management cho Exam Staff.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET  /api/staff/appeals}                        — Danh sách + filter + stats</li>
 *   <li>{@code GET  /api/staff/appeals/{appealId}}             — Chi tiết đơn</li>
 *   <li>{@code PUT  /api/staff/appeals/{appealId}/assign}      — Phân công giảng viên</li>
 *   <li>{@code GET  /api/staff/appeals/lecturers}              — Danh sách giảng viên dropdown</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/staff/appeals")
@RequiredArgsConstructor
@Slf4j
public class StaffAppealController {

    private final StaffAppealService staffAppealService;

    /**
     * Lấy danh sách đơn phúc khảo có filter, search, phân trang và overview stats.
     *
     * @param status  filter theo trạng thái (tuỳ chọn)
     * @param keyword tìm kiếm tên SV / MSSV / tên bài thi (tuỳ chọn)
     * @param page    số trang (default 0)
     * @param size    số item mỗi trang (default 10)
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> getAppeals(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(value = "semester", required = false) String semester,
            @RequestParam(value = "examName", required = false) String examName,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        var response = staffAppealService.getAppeals(status, keyword, semester, examName, page, size);
        return ResponseEntity.ok(buildResponse(true, "Lấy danh sách phúc khảo thành công.", response));
    }

    /**
     * Lấy danh sách giảng viên có thể phân công (cho dropdown).
     * Kèm số appeal đang xử lý của từng giảng viên.
     */
    @GetMapping("/lecturers")
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> getLecturerOptions() {
        var response = staffAppealService.getLecturerOptions();
        return ResponseEntity.ok(buildResponse(true, "Lấy danh sách giảng viên thành công.", response));
    }

    /**
     * Lấy chi tiết một đơn phúc khảo.
     *
     * @param appealId UUID đơn phúc khảo
     */
    @GetMapping("/{appealId}")
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> getAppealDetail(@PathVariable("appealId") UUID appealId) {
        var response = staffAppealService.getAppealDetail(appealId);
        return ResponseEntity.ok(buildResponse(true, "Lấy chi tiết phúc khảo thành công.", response));
    }

    /**
     * Phân công giảng viên cho đơn phúc khảo.
     * Appeal chuyển sang PROCESSING. Deadline tự tính từ config.
     *
     * @param appealId       UUID đơn phúc khảo
     * @param request        body chứa lecturerId
     * @param authentication JWT của staff đang login
     */
    @PutMapping("/{appealId}/assign")
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> assignLecturer(
            @PathVariable("appealId") UUID appealId,
            @Valid @RequestBody AssignAppealRequest request,
            Authentication authentication) {

        UUID staffId = extractUserId(authentication);
        log.info("[StaffAppealController] Staff {} phân công appeal {} cho lecturer {}",
                staffId, appealId, request.getLecturerId());

        var response = staffAppealService.assignLecturer(appealId, request, staffId);
        return ResponseEntity.ok(buildResponse(true,
                "Phân công giảng viên thành công. Đơn phúc khảo đang được xử lý.", response));
    }

    /**
     * Staff Xác nhận kết quả phúc khảo (Approve / Deny).
     *
     * @param appealId       UUID đơn phúc khảo
     * @param request        body chứa quyết định (isApprove)
     * @param authentication JWT
     */
    @PutMapping("/{appealId}/confirm")
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> confirmAppeal(
            @PathVariable("appealId") UUID appealId,
            @Valid @RequestBody agsfjope.backend.application.dtos.requests.appeal.ConfirmAppealRequest request,
            Authentication authentication) {

        UUID staffId = extractUserId(authentication);
        log.info("[StaffAppealController] Staff {} xác nhận appeal {}", staffId, appealId);

        var response = staffAppealService.confirmAppeal(appealId, request, staffId);
        
        String msg = Boolean.TRUE.equals(request.getIsApprove()) 
                ? "Đã duyệt và cập nhật điểm chính thức." 
                : "Đã từ chối đơn phúc khảo, giữ nguyên điểm gốc.";
                
        return ResponseEntity.ok(buildResponse(true, msg, response));
    }

    @GetMapping("/{appealId}/download")
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> downloadSubmission(
            @PathVariable("appealId") UUID appealId,
            Authentication authentication) {

        log.info("[StaffAppealController] Staff tải bài nộp của appeal {}", appealId);

        var appealDetail = staffAppealService.getAppealDetail(appealId);
        java.io.InputStream stream = staffAppealService.downloadSubmission(appealId);

        String filename = appealDetail.getSubmissionFileName();
        if (filename == null || filename.isBlank()) {
            filename = "submission.zip";
        }

        org.springframework.http.MediaType mediaType = filename.toLowerCase().endsWith(".rar")
                ? org.springframework.http.MediaType.parseMediaType("application/x-rar-compressed")
                : org.springframework.http.MediaType.parseMediaType("application/zip");

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(new org.springframework.core.io.InputStreamResource(stream));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("Không tìm thấy thông tin xác thực.");
        }
        if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUser().getUserId();
        }
        throw new IllegalStateException("Không thể xác định danh tính người dùng.");
    }

    private Map<String, Object> buildResponse(boolean success, String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("data", data);
        response.put("errors", null);
        return response;
    }
}
