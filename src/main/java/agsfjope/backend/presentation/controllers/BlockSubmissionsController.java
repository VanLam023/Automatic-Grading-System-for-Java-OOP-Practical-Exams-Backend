package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.responses.submission.SubmissionListItemResponse;
import agsfjope.backend.core.enums.GradingResultStatus;
import agsfjope.backend.core.enums.SubmissionStatus;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import agsfjope.backend.core.repositories.submission.projections.SubmissionListRowProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    private final SubmissionRepository submissionRepository;

    private static final String STAFF_ROLES =
            "hasAnyAuthority('EXAM_STAFF','ROLE_EXAM_STAFF','SYSTEM_ADMIN','ROLE_SYSTEM_ADMIN')";

    @GetMapping("/api/exams/{examId}/blocks/{blockId}/submissions")
    @PreAuthorize(STAFF_ROLES)
    public ResponseEntity<Map<String, Object>> getBlockSubmissions(
            @PathVariable UUID examId,
            @PathVariable UUID blockId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status
    ) {
        String keyword = StringUtils.hasText(search) ? search.trim() : "";
        String normalizedStatus = null;
        if (StringUtils.hasText(status)) {
            try {
                normalizedStatus = SubmissionStatus.valueOf(status.trim().toUpperCase()).name();
            } catch (IllegalArgumentException ignored) {
                normalizedStatus = null;
            }
        }

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        Page<SubmissionListRowProjection> pageResult = normalizedStatus == null
                ? submissionRepository.findSubmissionListPageByBlock(examId, blockId, keyword, pageable)
                : submissionRepository.findSubmissionListPageByBlockAndStatus(examId, blockId, normalizedStatus, keyword, pageable);

        List<SubmissionListItemResponse> items = pageResult.getContent().stream()
                .map(this::toResponse)
                .toList();

        long totalAll = submissionRepository.countByBlock_BlockId(blockId);
        long totalSubmitted = submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.SUBMITTED);
        long totalGrading = submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADING);
        long totalGraded = submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADED);

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", totalAll);
        stats.put("submitted", totalSubmitted);
        stats.put("grading", totalGrading);
        stats.put("graded", totalGraded);

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", pageResult.getNumber());
        pagination.put("size", pageResult.getSize());
        pagination.put("totalElements", pageResult.getTotalElements());
        pagination.put("totalPages", Math.max(1, pageResult.getTotalPages()));
        pagination.put("isFirst", pageResult.getNumber() == 0);
        pagination.put("isLast", pageResult.getNumber() >= Math.max(0, pageResult.getTotalPages() - 1));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Danh sách bài nộp: " + pageResult.getTotalElements() + " bài.");
        response.put("data", items);
        response.put("stats", stats);
        response.put("pagination", pagination);
        response.put("errors", null);
        return ResponseEntity.ok(response);
    }

    private SubmissionListItemResponse toResponse(SubmissionListRowProjection row) {
        SubmissionStatus submissionStatus = row.getSubmissionStatus() != null
                ? SubmissionStatus.valueOf(row.getSubmissionStatus())
                : null;

        GradingResultStatus gradingStatus = row.getGradingStatus() != null
                ? GradingResultStatus.valueOf(row.getGradingStatus())
                : null;

        return SubmissionListItemResponse.builder()
                .submissionId(row.getSubmissionId())
                .fileName(row.getFileName())
                .fileSizeBytes(row.getFileSizeBytes())
                .submissionStatus(submissionStatus)
                .submittedAt(row.getSubmittedAt())
                .studentId(row.getStudentId())
                .studentName(row.getStudentName())
                .studentCode(row.getStudentCode())
                .studentEmail(row.getStudentEmail())
                .gradingResultId(row.getGradingResultId())
                .gradingStatus(gradingStatus)
                .totalScore(row.getTotalScore())
                .maxScore(row.getMaxScore())
                .gradedAt(row.getGradedAt())
                .build();
    }
}