package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.responses.submission.SubmissionListItemResponse;
import agsfjope.backend.core.entities.GradingResult;
import agsfjope.backend.core.entities.Submission;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.SubmissionStatus;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Controller riêng để list toàn bộ submissions của một block (EXAM_STAFF).
 *
 * <p>Endpoint: {@code GET /api/exams/{examId}/blocks/{blockId}/submissions}</p>
 *
 * <p>Hỗ trợ phân trang, tìm kiếm theo tên/MSSV và lọc theo trạng thái.</p>
 */
@RestController
@RequiredArgsConstructor
public class BlockSubmissionsController {

    private final SubmissionRepository    submissionRepository;
    private final GradingResultRepository gradingResultRepository;

    private static final String STAFF_ROLES =
            "hasAnyAuthority('EXAM_STAFF','ROLE_EXAM_STAFF','SYSTEM_ADMIN','ROLE_SYSTEM_ADMIN')";

    /**
     * Trả danh sách phân trang các bài nộp trong một block.
     * Filter và search được thực hiện in-memory sau khi load.
     */
    @GetMapping("/api/exams/{examId}/blocks/{blockId}/submissions")
    @PreAuthorize(STAFF_ROLES)
    public ResponseEntity<Map<String, Object>> getBlockSubmissions(
            @PathVariable UUID examId,
            @PathVariable UUID blockId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String search,
            @RequestParam(required = false)    String status
    ) {
        // 1. Load ALL submissions for the block (ordered by submittedAt DESC)
        List<Submission> allSubmissions =
                submissionRepository.findAllByBlock_BlockIdOrderBySubmittedAtDesc(blockId);

        // 2. Build grading map for O(1) lookup
        Map<UUID, GradingResult> gradingMap = new HashMap<>();
                gradingResultRepository.findAllBySubmission_Block_BlockId(blockId)
                                .forEach(gr -> {
                                        if (gr != null && gr.getSubmission() != null && gr.getSubmission().getSubmissionId() != null) {
                                                gradingMap.put(gr.getSubmission().getSubmissionId(), gr);
                                        }
                                });

        // 3. Map to DTOs
        List<SubmissionListItemResponse> allItems = allSubmissions.stream().map(sub -> {
            Optional<GradingResult> gr = Optional.ofNullable(gradingMap.get(sub.getSubmissionId()));
            User student = sub.getStudent();
            return SubmissionListItemResponse.builder()
                    .submissionId(sub.getSubmissionId())
                    .fileName(sub.getFileName())
                    .fileSizeBytes(sub.getFileSizeBytes())
                    .submissionStatus(sub.getStatus())
                    .submittedAt(sub.getSubmittedAt())
                    .studentId(student != null ? student.getUserId() : null)
                    .studentName(student != null ? student.getFullName() : null)
                    .studentCode(student != null ? student.getMssv() : null)
                    .studentEmail(student != null ? student.getEmail() : null)
                    .gradingResultId(gr.map(GradingResult::getGradingResultId).orElse(null))
                    .gradingStatus(gr.map(GradingResult::getStatus).orElse(null))
                    .totalScore(gr.map(GradingResult::getTotalScore).orElse(null))
                    .maxScore(gr.map(GradingResult::getMaxScore).orElse(null))
                    .gradedAt(gr.map(GradingResult::getUpdatedAt).orElse(null))
                    .build();
        }).toList();

        // 4. Stats — full block counts (before any filter)
        long totalAll     = allItems.size();
        long totalSubmitted = allItems.stream().filter(i -> i.getSubmissionStatus() == SubmissionStatus.SUBMITTED).count();
        long totalGrading   = allItems.stream().filter(i -> i.getSubmissionStatus() == SubmissionStatus.GRADING).count();
        long totalGraded    = allItems.stream().filter(i -> i.getSubmissionStatus() == SubmissionStatus.GRADED).count();
        long totalFailed    = allItems.stream().filter(i -> i.getSubmissionStatus() == SubmissionStatus.GRADING_FAILED).count();

        // 5. In-memory filter: status
        SubmissionStatus statusFilter = null;
        if (StringUtils.hasText(status)) {
            try { statusFilter = SubmissionStatus.valueOf(status.toUpperCase()); }
            catch (IllegalArgumentException ignored) { /* treat as "all" */ }
        }
        final SubmissionStatus finalStatus = statusFilter;

        List<SubmissionListItemResponse> filtered = allItems;
        if (finalStatus != null) {
            filtered = filtered.stream()
                    .filter(i -> i.getSubmissionStatus() == finalStatus)
                    .toList();
        }

        // 6. In-memory filter: keyword (name or MSSV)
        if (StringUtils.hasText(search)) {
            String q = search.trim().toLowerCase();
            filtered = filtered.stream()
                    .filter(i -> {
                        String name = i.getStudentName() != null ? i.getStudentName().toLowerCase() : "";
                        String code = i.getStudentCode() != null ? i.getStudentCode().toLowerCase() : "";
                        return name.contains(q) || code.contains(q);
                    })
                    .toList();
        }

        // 7. Pagination
        int totalFiltered = filtered.size();
        int totalPages    = (int) Math.ceil((double) totalFiltered / size);
        int safePage      = Math.max(0, Math.min(page, totalPages - 1));
        int fromIdx       = safePage * size;
        int toIdx         = Math.min(fromIdx + size, totalFiltered);

        List<SubmissionListItemResponse> pageItems = (fromIdx < totalFiltered)
                ? filtered.subList(fromIdx, toIdx)
                : List.of();

        // 8. Build response
        Map<String, Object> stats = new HashMap<>();
        stats.put("total",     totalAll);
        stats.put("submitted", totalSubmitted);
        stats.put("grading",   totalGrading);
        stats.put("graded",    totalGraded);
        stats.put("failed",    totalFailed);

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page",          safePage);
        pagination.put("size",          size);
        pagination.put("totalElements", totalFiltered);
        pagination.put("totalPages",    Math.max(1, totalPages));
        pagination.put("isFirst",       safePage == 0);
        pagination.put("isLast",        safePage >= totalPages - 1);

        Map<String, Object> response = new HashMap<>();
        response.put("success",    true);
        response.put("message",    "Danh sách bài nộp: " + totalFiltered + " bài.");
        response.put("data",       pageItems);
        response.put("stats",      stats);
        response.put("pagination", pagination);
        response.put("errors",     null);
        return ResponseEntity.ok(response);
    }
}
