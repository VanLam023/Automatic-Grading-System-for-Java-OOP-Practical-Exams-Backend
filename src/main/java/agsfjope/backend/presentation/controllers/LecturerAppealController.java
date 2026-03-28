package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.appealservices.LecturerAppealService;
import agsfjope.backend.application.dtos.requests.appeal.ReviewAppealRequest;
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

@RestController
@RequestMapping("/api/lecturer/appeals")
@RequiredArgsConstructor
@Slf4j
public class LecturerAppealController {

    private final LecturerAppealService lecturerAppealService;

    @GetMapping
    @PreAuthorize("hasRole('LECTURER')")
    public ResponseEntity<Map<String, Object>> getAppeals(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Authentication authentication) {

        UUID lecturerId = extractUserId(authentication);
        var response = lecturerAppealService.getAppeals(lecturerId, status, keyword, page, size);
        return ResponseEntity.ok(buildResponse(true, "Lấy danh sách đơn chấm phúc khảo thành công.", response));
    }

    @GetMapping("/{appealId}")
    @PreAuthorize("hasRole('LECTURER')")
    public ResponseEntity<Map<String, Object>> getAppealDetail(
            @PathVariable("appealId") UUID appealId,
            Authentication authentication) {

        UUID lecturerId = extractUserId(authentication);
        var response = lecturerAppealService.getAppealDetail(lecturerId, appealId);
        return ResponseEntity.ok(buildResponse(true, "Lấy chi tiết đơn chấm phúc khảo thành công.", response));
    }

    @PutMapping("/{appealId}/review")
    @PreAuthorize("hasRole('LECTURER')")
    public ResponseEntity<Map<String, Object>> submitReview(
            @PathVariable("appealId") UUID appealId,
            @Valid @RequestBody ReviewAppealRequest request,
            Authentication authentication) {

        UUID lecturerId = extractUserId(authentication);
        log.info("[LecturerAppealController] Giảng viên {} nộp kết quả chấm cho appeal {}", lecturerId, appealId);

        var response = lecturerAppealService.submitReview(lecturerId, appealId, request);
        return ResponseEntity.ok(buildResponse(true, "Nộp kết quả báo cáo chấm phúc khảo thành công.", response));
    }

    @GetMapping("/{appealId}/download")
    @PreAuthorize("hasRole('LECTURER')")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> downloadSubmission(
            @PathVariable("appealId") UUID appealId,
            Authentication authentication) {

        UUID lecturerId = extractUserId(authentication);
        log.info("[LecturerAppealController] Giảng viên {} tải bài nộp của appeal {}", lecturerId, appealId);

        var appealDetail = lecturerAppealService.getAppealDetail(lecturerId, appealId);
        java.io.InputStream stream = lecturerAppealService.downloadSubmission(lecturerId, appealId);

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
