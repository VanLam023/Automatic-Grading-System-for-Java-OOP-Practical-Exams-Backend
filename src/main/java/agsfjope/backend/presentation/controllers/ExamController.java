package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.requests.exam.CreateExamRequest;
import agsfjope.backend.application.dtos.requests.exam.UpdateExamRequest;
import agsfjope.backend.application.dtos.responses.exam.ExamResponse;
import agsfjope.backend.application.examservices.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller exposing Exam management APIs.
 * All endpoints are restricted to {@code EXAM_STAFF} role only (BR-05).
 *
 * <p>Base URL: {@code /api/exams}</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code POST /api/exams} — Create a new exam</li>
 *   <li>{@code GET /api/exams} — Get all exams</li>
 *   <li>{@code GET /api/exams/{examId}} — Get exam by ID</li>
 *   <li>{@code PUT /api/exams/{examId}} — Update exam</li>
 *   <li>{@code DELETE /api/exams/{examId}} — Soft-delete exam</li>
 * </ul>
 *
 * <p>Authorization: requires valid JWT token with authority {@code EXAM_STAFF}.</p>
 *
 * Business logic is delegated to {@link ExamService}.
 */
@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
public class ExamController {

    private final ExamService examService;

    /**
     * Create a new exam.
     *
     * <p>Method: POST</p>
     * <p>URL: /api/exams</p>
     * <p>Header: Authorization: Bearer &lt;jwt_token&gt;</p>
     * <p>Body example:</p>
     * <pre>
     * {
     *   "name": "OOP Practical Exam",
     *   "semester": "SP25",
     *   "academicYear": "2024-2025",
     *   "startTime": "2025-04-10T08:00:00+07:00",
     *   "endTime": "2025-04-10T11:00:00+07:00",
     *   "gradingMode": "MODE_1"
     * }
     * </pre>
     *
     * @param request DTO containing new exam information
     * @return created exam data wrapped in standard success response
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createExam(@Valid @RequestBody CreateExamRequest request) {
        ExamResponse exam = examService.createExam(request);
        return ResponseEntity.ok(buildSuccessResponse("MSG-19: Tạo kỳ thi thành công", exam));
    }

    /**
     * Get a list of all active (non-deleted) exams.
     *
     * <p>Method: GET</p>
     * <p>URL: /api/exams</p>
     * <p>Header: Authorization: Bearer &lt;jwt_token&gt;</p>
     *
     * @return list of exams wrapped in standard success response
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllExams() {
        List<ExamResponse> exams = examService.getAllExams();
        return ResponseEntity.ok(buildSuccessResponse("Lấy danh sách kỳ thi thành công", exams));
    }

    /**
     * Get exam details by ID.
     *
     * <p>Method: GET</p>
     * <p>URL: /api/exams/{examId}</p>
     * <p>Header: Authorization: Bearer &lt;jwt_token&gt;</p>
     *
     * @param examId UUID of the exam
     * @return exam detail wrapped in standard success response
     */
    @GetMapping("/{examId}")
    public ResponseEntity<Map<String, Object>> getExamById(@PathVariable UUID examId) {
        ExamResponse exam = examService.getExamById(examId);
        return ResponseEntity.ok(buildSuccessResponse("Lấy thông tin kỳ thi thành công", exam));
    }

    /**
     * Update an existing exam.
     *
     * <p>Method: PUT</p>
     * <p>URL: /api/exams/{examId}</p>
     * <p>Header: Authorization: Bearer &lt;jwt_token&gt;</p>
     * <p>Body: only include fields to update, null fields are ignored.</p>
     *
     * @param examId  UUID of the exam
     * @param request update payload
     * @return updated exam data wrapped in standard success response
     */
    @PutMapping("/{examId}")
    public ResponseEntity<Map<String, Object>> updateExam(
            @PathVariable UUID examId,
            @Valid @RequestBody UpdateExamRequest request
    ) {
        ExamResponse exam = examService.updateExam(examId, request);
        return ResponseEntity.ok(buildSuccessResponse("MSG-20: Cập nhật kỳ thi thành công", exam));
    }

    /**
     * Soft-delete an exam.
     * Fails with 409 Conflict if any submission already exists in this exam's blocks (BR-12).
     *
     * <p>Method: DELETE</p>
     * <p>URL: /api/exams/{examId}</p>
     * <p>Header: Authorization: Bearer &lt;jwt_token&gt;</p>
     *
     * @param examId UUID of the exam
     * @return success message
     */
    @DeleteMapping("/{examId}")
    public ResponseEntity<Map<String, Object>> deleteExam(@PathVariable UUID examId) {
        examService.deleteExam(examId);
        return ResponseEntity.ok(buildSuccessResponse("MSG-21: Xóa kỳ thi thành công", null));
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    /**
     * Helper method: builds the standard API success response payload.
     * Format: { success, message, data, errors }
     *
     * @param message human-readable success message (include MSG code where applicable)
     * @param data    response payload (nullable for void operations)
     * @return response map
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