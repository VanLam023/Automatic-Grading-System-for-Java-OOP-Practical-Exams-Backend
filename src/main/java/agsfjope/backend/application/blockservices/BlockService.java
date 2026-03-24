package agsfjope.backend.application.blockservices;

import agsfjope.backend.application.dtos.requests.block.UpdateBlockRequest;
import agsfjope.backend.application.dtos.responses.block.BlockResponse;
import agsfjope.backend.core.entities.Exam;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for Block management.
 *
 * <p>Blocks are fixed per exam (Block 10 + Block 3 — BR-08).
 * They are auto-created when an exam is created, and cannot be added or removed.</p>
 */
public interface BlockService {

    /**
     * Auto-creates the 2 fixed blocks (Block 10 + Block 3) for a newly created exam.
     * Called internally by {@code ExamService.createExam()} — not exposed via API.
     *
     * <p>Default values: examDate, startTime, endTime are set to the exam's startTime
     * as placeholders — Exam Staff must update them via {@link #updateBlock}.</p>
     *
     * @param exam the newly saved Exam entity
     */
    void createDefaultBlocks(Exam exam);

    /**
     * Returns all blocks for the given exam, ordered by name.
     *
     * @param examId exam identifier
     * @return list of block responses (always 2: Block 10 + Block 3)
     */
    List<BlockResponse> getBlocksByExamId(UUID examId);

    /**
     * Returns detailed information for a single block.
     * Validates that the block belongs to the given exam.
     *
     * @param examId  exam identifier (for ownership check)
     * @param blockId block identifier
     * @return block response
     * @throws agsfjope.backend.core.exceptions.auth.NotFoundException if block not found
     * @throws IllegalArgumentException if block does not belong to the given exam
     */
    BlockResponse getBlockById(UUID examId, UUID blockId);

    /**
     * Updates a block's exam schedule (date + time window) and optional description.
     *
     * <p>Validations applied:</p>
     * <ul>
     *   <li>EndTime must be after StartTime (DTO validation + DB constraint).</li>
     *   <li>Block StartTime + EndTime must fall within parent Exam's StartTime — EndTime window.</li>
     * </ul>
     *
     * @param examId  exam identifier (for ownership validation)
     * @param blockId block identifier
     * @param request update payload
     * @return updated block response
     */
    BlockResponse updateBlock(UUID examId, UUID blockId, UpdateBlockRequest request);
}
