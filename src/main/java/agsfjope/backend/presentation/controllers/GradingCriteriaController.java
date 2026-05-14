package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.requests.grading.GradingCriteriaRequest;
import agsfjope.backend.application.dtos.responses.grading.GradingCriteriaResponse;
import agsfjope.backend.application.gradingservices.GradingCriteriaService;
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
 * REST Controller for structural OOP grading criteria.
 *
 * <p>Nested resource under Exams → Questions:
 * {@code /api/exams/{examId}/questions/{questionId}/grading-criteria}</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET    .../grading-criteria}           — List all criteria for a question</li>
 *   <li>{@code POST   .../grading-criteria}           — Create a single criterion</li>
 *   <li>{@code PUT    .../grading-criteria/{id}}      — Update a single criterion</li>
 *   <li>{@code DELETE .../grading-criteria/{id}}      — Delete a single criterion</li>
 *   <li>{@code PUT    .../grading-criteria/batch}     — Replace ALL criteria (atomic)</li>
 * </ul>
 *
 * <p>Authorization: EXAM_STAFF only (read + write).</p>
 */
@RestController
@RequestMapping("/api/exams/{examId}/questions/{questionId}/grading-criteria")
@RequiredArgsConstructor
public class GradingCriteriaController {

    private final GradingCriteriaService criteriaService;

    // ─── LIST ────────────────────────────────────────────────────────────────

    /**
     * GET /api/exams/{examId}/questions/{questionId}/grading-criteria
     * Returns all OOP criteria for the question, ordered by displayOrder ASC.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF', 'SYSTEM_ADMIN', 'ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable UUID examId,
            @PathVariable UUID questionId
    ) {
        List<GradingCriteriaResponse> data = criteriaService.listByQuestion(questionId);
        return ok("Lấy danh sách tiêu chí thành công.", data);
    }

    // ─── CREATE ──────────────────────────────────────────────────────────────

    /**
     * POST /api/exams/{examId}/questions/{questionId}/grading-criteria
     * Creates a single structural criterion for the question.
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable UUID examId,
            @PathVariable UUID questionId,
            @Valid @RequestBody GradingCriteriaRequest request
    ) {
        GradingCriteriaResponse created = criteriaService.create(questionId, request);
        return ok("Tạo tiêu chí thành công.", created);
    }

    // ─── UPDATE ──────────────────────────────────────────────────────────────

    /**
     * PUT /api/exams/{examId}/questions/{questionId}/grading-criteria/{criteriaId}
     * Updates an existing criterion.
     */
    @PutMapping("/{criteriaId}")
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable UUID examId,
            @PathVariable UUID questionId,
            @PathVariable UUID criteriaId,
            @Valid @RequestBody GradingCriteriaRequest request
    ) {
        GradingCriteriaResponse updated = criteriaService.update(questionId, criteriaId, request);
        return ok("Cập nhật tiêu chí thành công.", updated);
    }

    // ─── DELETE ──────────────────────────────────────────────────────────────

    /**
     * DELETE /api/exams/{examId}/questions/{questionId}/grading-criteria/{criteriaId}
     * Deletes a single criterion.
     */
    @DeleteMapping("/{criteriaId}")
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable UUID examId,
            @PathVariable UUID questionId,
            @PathVariable UUID criteriaId
    ) {
        criteriaService.delete(questionId, criteriaId);
        return ok("Xóa tiêu chí thành công.", null);
    }

    // ─── BATCH REPLACE ───────────────────────────────────────────────────────

    /**
     * PUT /api/exams/{examId}/questions/{questionId}/grading-criteria/batch
     *
     * <p>Atomically replaces ALL criteria for the question with the provided list.
     * Sending an empty list removes all criteria.</p>
     *
     * <p>Business validation (also enforced by frontend):</p>
     * <ul>
     *   <li>SUM(maxScore) of all criteria must not exceed question.maxScore.</li>
     * </ul>
     */
    @PutMapping("/batch")
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> saveBatch(
            @PathVariable UUID examId,
            @PathVariable UUID questionId,
            @Valid @RequestBody List<GradingCriteriaRequest> requests
    ) {
        List<GradingCriteriaResponse> saved = criteriaService.saveBatch(questionId, requests);
        return ok("Lưu " + saved.size() + " tiêu chí thành công.", saved);
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", message);
        body.put("data", data);
        body.put("errors", null);
        return ResponseEntity.ok(body);
    }
}
