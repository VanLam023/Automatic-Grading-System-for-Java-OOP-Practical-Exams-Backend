package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.responses.submission.SubmissionResponse;
import agsfjope.backend.application.submissionservices.SubmissionService;
import agsfjope.backend.infrastructure.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Student Submission management.
 *
 * <p>Submissions are nested resources under Exams → Blocks:
 * {@code /api/exams/{examId}/blocks/{blockId}/submission}</p>
 *
 * <p>Business Rules enforced (via {@link SubmissionService}):</p>
 * <ul>
 *   <li><strong>BR-14</strong>: Exam must be ONGOING to accept submissions.</li>
 *   <li><strong>BR-15</strong>: Archive structure: {@code {n}/run/*.jar} + {@code {n}/src/*.java}.</li>
 *   <li><strong>BR-16</strong>: File size limited by SystemConfig ({@code MAX_UPLOAD_SIZE_MB}).</li>
 *   <li><strong>BR-17</strong>: Resubmit fully overwrites the prior submission.</li>
 *   <li><strong>BR-18</strong>: One active submission per student per block.</li>
 * </ul>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code POST   /api/exams/{examId}/blocks/{blockId}/submission}          — Nộp bài (.zip/.rar)</li>
 *   <li>{@code GET    /api/exams/{examId}/blocks/{blockId}/submission}          — Xem bài nộp của mình</li>
 *   <li>{@code GET    /api/exams/{examId}/blocks/{blockId}/submission/download} — Download file bài nộp</li>
 * </ul>
 *
 * <p>Authorization:</p>
 * <ul>
 *   <li>POST: {@code STUDENT} only.</li>
 *   <li>GET / Download: {@code STUDENT}, {@code EXAM_STAFF}, {@code SYSTEM_ADMIN}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/exams/{examId}/blocks/{blockId}/submission")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    // ─── SUBMIT ──────────────────────────────────────────────────────────────

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('STUDENT', 'ROLE_STUDENT')")
    public ResponseEntity<Map<String, Object>> submit(
            @PathVariable UUID examId,
            @PathVariable UUID blockId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        UUID studentId = extractUserId(authentication);
        SubmissionResponse response = submissionService.submit(examId, blockId, studentId, file);

        String msg = response.isResubmit()
                ? "Nộp lại bài thành công. Bài nộp trước đó đã bị xóa."
                : "Bài làm đã được nộp thành công.";

        return ResponseEntity.ok(buildSuccessResponse(msg, response));
    }

    // ─── GET MY SUBMISSION ────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyAuthority('STUDENT', 'ROLE_STUDENT', 'EXAM_STAFF', 'ROLE_EXAM_STAFF', 'SYSTEM_ADMIN', 'ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> getMySubmission(
            @PathVariable UUID examId,
            @PathVariable UUID blockId,
            Authentication authentication
    ) {
        UUID studentId = extractUserId(authentication);
        SubmissionResponse response = submissionService.getMySubmission(examId, blockId, studentId);
        return ResponseEntity.ok(buildSuccessResponse("Lấy thông tin bài nộp thành công.", response));
    }

    // ─── DOWNLOAD ────────────────────────────────────────────────────────────

    @GetMapping("/download")
    @PreAuthorize("hasAnyAuthority('STUDENT', 'ROLE_STUDENT', 'EXAM_STAFF', 'ROLE_EXAM_STAFF', 'SYSTEM_ADMIN', 'ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<InputStreamResource> downloadSubmission(
            @PathVariable UUID examId,
            @PathVariable UUID blockId,
            Authentication authentication
    ) {
        UUID studentId = extractUserId(authentication);

        SubmissionResponse meta = submissionService.getMySubmission(examId, blockId, studentId);
        InputStream stream = submissionService.downloadMySubmission(examId, blockId, studentId);

        String filename = meta.getFileName();
        MediaType mediaType = filename.toLowerCase().endsWith(".rar")
                ? MediaType.parseMediaType("application/x-rar-compressed")
                : MediaType.parseMediaType("application/zip");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(meta.getFileSizeBytes()))
                .contentType(mediaType)
                .body(new InputStreamResource(stream));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

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

    private Map<String, Object> buildSuccessResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        response.put("errors", null);
        return response;
    }
}
