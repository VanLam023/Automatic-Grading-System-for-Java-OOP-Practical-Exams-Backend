package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.requests.exam.CreateExamRequest;
import agsfjope.backend.application.dtos.requests.exam.UpdateExamRequest;
import agsfjope.backend.application.dtos.responses.exam.ExamResponse;
import agsfjope.backend.application.examservices.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> createExam(@Valid @RequestBody CreateExamRequest request) {
        ExamResponse exam = examService.createExam(request);
        return ResponseEntity.ok(buildSuccessResponse("MSG-19: Tạo kỳ thi thành công", exam));
    }

    /**
     * Get a paginated + searchable list of all active (non-deleted) exams.
     *
     * <p>Method: GET</p>
     * <p>URL: /api/exams?page=0&size=20&sort=createdAt,desc&name=OOP&semester=SP26&academicYear=2025-2026</p>
     *
     * @param page         page number (0-indexed, default 0)
     * @param size         items per page (default 20)
     * @param sort         sort field and direction (default: createdAt,desc)
     * @param name         partial name search (optional)
     * @param semester     partial semester search (optional, e.g. "SP26")
     * @param academicYear exact academic year filter (optional, e.g. "2025-2026")
     * @return paginated + filtered list of exams with metadata
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF', 'SYSTEM_ADMIN', 'ROLE_SYSTEM_ADMIN', 'STUDENT', 'ROLE_STUDENT')")
    public ResponseEntity<Map<String, Object>> getAllExams(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String academicYear
    ) {
        String[] sortParts = sort.split(",");
        String sortField = sortParts[0];
        Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        // Use searchExams when any filter param is provided; otherwise fallback to simple list
        boolean hasFilter = (name != null && !name.isBlank())
                         || (semester != null && !semester.isBlank())
                         || (academicYear != null && !academicYear.isBlank());

        Page<ExamResponse> result = hasFilter
                ? examService.searchExams(name, semester, academicYear, pageable)
                : examService.getAllExams(pageable);

        Map<String, Object> data = new HashMap<>();
        data.put("content",       result.getContent());
        data.put("currentPage",   result.getNumber());
        data.put("totalItems",    result.getTotalElements());
        data.put("totalPages",    result.getTotalPages());
        data.put("pageSize",      result.getSize());
        data.put("isLast",        result.isLast());

        return ResponseEntity.ok(buildSuccessResponse("Lấy danh sách kỳ thi thành công", data));
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
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF', 'SYSTEM_ADMIN', 'ROLE_SYSTEM_ADMIN', 'STUDENT', 'ROLE_STUDENT')")
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
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
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
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
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