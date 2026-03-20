package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.blockservices.BlockService;
import agsfjope.backend.application.dtos.requests.block.UpdateBlockRequest;
import agsfjope.backend.application.dtos.responses.block.BlockResponse;
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
 * REST Controller for Block management endpoints.
 *
 * <p>Blocks are nested resources under Exams: {@code /api/exams/{examId}/blocks}</p>
 *
 * <p>Blocks are auto-created (Block 10 + Block 3) when an exam is created (BR-08).
 * This controller only exposes GET and PUT — no POST or DELETE.</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET  /api/exams/{examId}/blocks}           — List blocks of an exam</li>
 *   <li>{@code GET  /api/exams/{examId}/blocks/{blockId}} — Get block detail</li>
 *   <li>{@code PUT  /api/exams/{examId}/blocks/{blockId}} — Update block schedule</li>
 * </ul>
 *
 * <p>Authorization: {@code EXAM_STAFF} can read and update. {@code SYSTEM_ADMIN} and {@code ADMIN} can read.</p>
 */
@RestController
@RequestMapping("/api/exams/{examId}/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    /**
     * GET /api/exams/{examId}/blocks
     * Returns all blocks for the given exam (always Block 10 + Block 3).
     *
     * @param examId exam identifier (path variable)
     * @return list of block responses
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF', 'SYSTEM_ADMIN', 'ROLE_SYSTEM_ADMIN', 'ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<List<BlockResponse>> getBlocksByExam(@PathVariable UUID examId) {
        return ResponseEntity.ok(blockService.getBlocksByExamId(examId));
    }

    /**
     * GET /api/exams/{examId}/blocks/{blockId}
     * Returns detailed information for a single block.
     *
     * @param examId  exam identifier (path variable)
     * @param blockId block identifier (path variable)
     * @return block response
     */
    @GetMapping("/{blockId}")
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF', 'SYSTEM_ADMIN', 'ROLE_SYSTEM_ADMIN', 'ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<BlockResponse> getBlockById(
            @PathVariable UUID examId,
            @PathVariable UUID blockId
    ) {
        // Validate block belongs to this exam before returning
        return ResponseEntity.ok(blockService.getBlockById(examId, blockId));
    }

    /**
     * PUT /api/exams/{examId}/blocks/{blockId}
     * Updates a block's exam date + time window.
     *
     * <p>Exam Staff sets the actual exam schedule for each block after creating the exam.</p>
     *
     * @param examId  exam identifier (path variable)
     * @param blockId block identifier (path variable)
     * @param request update payload with examDate, startTime, endTime
     * @return updated block response
     */
    @PutMapping("/{blockId}")
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> updateBlock(
            @PathVariable UUID examId,
            @PathVariable UUID blockId,
            @Valid @RequestBody UpdateBlockRequest request
    ) {
        BlockResponse updated = blockService.updateBlock(examId, blockId, request);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Cập nhật lịch thi của block thành công.");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }
}
