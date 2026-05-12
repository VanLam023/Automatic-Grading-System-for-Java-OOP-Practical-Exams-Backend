package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.responses.exampaper.ExamPaperResponse;
import agsfjope.backend.application.exampaperservices.ExamPaperService;
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
 * REST Controller for ExamPaper (Upload Exam Paper) management.
 *
 * <p>ExamPapers are nested resources under Exams → Blocks:
 * {@code /api/exams/{examId}/blocks/{blockId}/exam-paper}</p>
 *
 * <p>Business Rules enforced (via {@link ExamPaperService}):</p>
 * <ul>
 *   <li><strong>BR-09</strong>: 1 Block = 1 ExamPaper. Re-uploading auto-overwrites.</li>
 *   <li><strong>BR-10</strong>: Archive is parsed automatically → Questions + TestCases.</li>
 *   <li><strong>BR-11</strong>: Cannot modify/delete if any student submission exists.</li>
 *   <li><strong>BR-16</strong>: Max file size = 20 MB.</li>
 * </ul>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code POST   /api/exams/{examId}/blocks/{blockId}/exam-paper}          — Upload đề thi (.zip/.rar)</li>
 *   <li>{@code GET    /api/exams/{examId}/blocks/{blockId}/exam-paper}          — Xem metadata + câu hỏi + test cases</li>
 *   <li>{@code DELETE /api/exams/{examId}/blocks/{blockId}/exam-paper}          — Xóa đề thi</li>
 *   <li>{@code GET    /api/exams/{examId}/blocks/{blockId}/exam-paper/download} — Download file gốc</li>
 * </ul>
 *
 * <p>Authorization:</p>
 * <ul>
 *   <li>Upload / Delete: {@code EXAM_STAFF} only.</li>
 *   <li>Get metadata:    {@code EXAM_STAFF}, {@code SYSTEM_ADMIN}, {@code ADMIN}.</li>
 *   <li>Download:        {@code EXAM_STAFF} only.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/exams/{examId}/blocks/{blockId}/exam-paper")
@RequiredArgsConstructor
public class ExamPaperController {

    private final ExamPaperService examPaperService;

    // ─── UPLOAD ──────────────────────────────────────────────────────────────

    /**
     * Upload an exam paper archive (.zip or .rar) for a specific block.
     *
     * <p>If the block already has an exam paper, it will be <strong>automatically overwritten</strong>
     * (BR-09) — both in the database and on MinIO storage.</p>
     *
     * <ul>
     *   <li>Method: {@code POST}</li>
     *   <li>Content-Type: {@code multipart/form-data}</li>
     *   <li>Form field: {@code file} — the archive file (.zip or .rar, max 20 MB)</li>
     * </ul>
     *
     * @param examId         exam identifier (path variable)
     * @param blockId        block identifier (path variable)
     * @param file           the uploaded archive file
     * @param authentication Spring Security authentication context (used to extract staffId)
     * @return ExamPaperResponse with full parsed structure (questions + test cases)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> uploadExamPaper(
            @PathVariable UUID examId,
            @PathVariable UUID blockId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "examCode", required = false) String examCode,
            Authentication authentication
    ) {
        UUID staffId = extractUserId(authentication);
        ExamPaperResponse response = examPaperService.upload(examId, blockId, staffId, file, examCode);
        return ResponseEntity.ok(buildSuccessResponse(
                "Upload đề thi thành công. Đã parse " + response.getTotalQuestions() +
                " câu hỏi với tổng " + response.getTotalTestCases() + " test cases.",
                response
        ));
    }

    // ─── GET ─────────────────────────────────────────────────────────────────

    /**
     * Get the exam paper metadata and parsed questions/test cases for a block.
     *
     * <ul>
     *   <li>Method: {@code GET}</li>
     * </ul>
     *
     * @param examId  exam identifier (path variable)
     * @param blockId block identifier (path variable)
     * @return ExamPaperResponse with metadata + questions + test cases
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF', 'SYSTEM_ADMIN', 'ROLE_SYSTEM_ADMIN', 'ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getExamPaper(
            @PathVariable UUID examId,
            @PathVariable UUID blockId
    ) {
        ExamPaperResponse response = examPaperService.getByBlock(examId, blockId);
        return ResponseEntity.ok(buildSuccessResponse("Lấy thông tin đề thi thành công.", response));
    }

    // ─── DELETE ──────────────────────────────────────────────────────────────

    /**
     * Delete the exam paper for a block.
     *
     * <p>Fails with <strong>409 Conflict</strong> if any student has already submitted for this block (BR-11).</p>
     *
     * <ul>
     *   <li>Method: {@code DELETE}</li>
     * </ul>
     *
     * @param examId  exam identifier (path variable)
     * @param blockId block identifier (path variable)
     * @return success message
     */
    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> deleteExamPaper(
            @PathVariable UUID examId,
            @PathVariable UUID blockId
    ) {
        examPaperService.deleteByBlock(examId, blockId);
        return ResponseEntity.ok(buildSuccessResponse("Xóa đề thi thành công.", null));
    }

    // ─── DOWNLOAD ────────────────────────────────────────────────────────────

    /**
     * Download the original exam paper archive file from MinIO storage.
     *
     * <p>Returns the raw binary stream of the uploaded archive (.zip or .rar)
     * with appropriate Content-Disposition header so browsers trigger a download.</p>
     *
     * <ul>
     *   <li>Method: {@code GET}</li>
     *   <li>Path: {@code .../exam-paper/download}</li>
     * </ul>
     *
     * @param examId  exam identifier (path variable)
     * @param blockId block identifier (path variable)
     * @return raw archive binary stream
     */
    @GetMapping("/download")
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
    public ResponseEntity<InputStreamResource> downloadExamPaper(
            @PathVariable UUID examId,
            @PathVariable UUID blockId
    ) {
        // Get paper metadata first to retrieve the filename for Content-Disposition
        ExamPaperResponse meta = examPaperService.getByBlock(examId, blockId);
        InputStream stream = examPaperService.downloadByBlock(examId, blockId);

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

    /**
     * Extracts the authenticated user's UUID from the Spring Security context.
     *
     * @param authentication the current Security authentication object
     * @return the logged-in user's UUID
     * @throws IllegalStateException if authentication is missing or not a known principal type
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
     * Builds the standard API success response.
     * Format: {@code { success: true, message: "...", data: {...}, errors: null }}
     */
    private Map<String, Object> buildSuccessResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        response.put("errors", null);
        return response;
    }
}
