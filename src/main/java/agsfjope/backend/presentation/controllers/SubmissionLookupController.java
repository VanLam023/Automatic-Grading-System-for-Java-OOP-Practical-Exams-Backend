package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.responses.submission.SubmissionResponse;
import agsfjope.backend.application.submissionservices.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for submission lookup by ID.
 *
 * <p>Provides a flat endpoint {@code GET /api/submissions/{submissionId}}
 * that returns submission metadata (fileName, fileSizeBytes, submittedAt, …)
 * without requiring the full exam/block path context.</p>
 *
 * <p>Authorization: STUDENT (own), EXAM_STAFF, SYSTEM_ADMIN.</p>
 */
@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionLookupController {

    private final SubmissionService submissionService;

    /**
     * Returns submission metadata for a specific submission by its UUID.
     *
     * <p>GET /api/submissions/{submissionId}</p>
     *
     * @param submissionId UUID of the target submission
     * @return {@link SubmissionResponse} with file info, status, submittedAt, etc.
     */
    @GetMapping("/{submissionId}")
    @PreAuthorize("hasAnyAuthority('STUDENT','ROLE_STUDENT','EXAM_STAFF','ROLE_EXAM_STAFF','SYSTEM_ADMIN','ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> getSubmissionById(
            @PathVariable UUID submissionId
    ) {
        SubmissionResponse response = submissionService.getSubmissionById(submissionId);
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "Lấy thông tin bài nộp thành công.");
        body.put("data", response);
        body.put("errors", null);
        return ResponseEntity.ok(body);
    }
}
