package agsfjope.backend.application.blockservices;

import agsfjope.backend.application.dtos.requests.block.CreateBlockRequest;
import agsfjope.backend.application.dtos.requests.block.UpdateBlockRequest;
import agsfjope.backend.application.dtos.responses.block.BlockResponse;
import agsfjope.backend.core.entities.Exam;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for Block management.
 *
 * <p>Blocks are created manually by Exam Staff via API.
 * Each block has a custom name unique within its exam.</p>
 */
public interface BlockService {

    /**
     * [DEPRECATED] Auto-creates the 2 fixed blocks (Block 10 + Block 3) for a newly created exam.
     * Kept for backward compatibility with older exams that have no blocks.
     *
     * @param exam the newly saved Exam entity
     */
    void createDefaultBlocks(Exam exam);

    /**
     * Creates a new block within the given exam.
     * Exam Staff provides a custom name (must be unique within the exam).
     * Schedule (examDate, startTime, endTime) is set later via updateBlock.
     *
     * @param examId  exam identifier
     * @param request creation payload containing name and optional description
     * @return created block response
     */
    BlockResponse createBlock(UUID examId, CreateBlockRequest request);

    /**
     * Returns all blocks for the given exam, ordered by name.
     *
     * @param examId exam identifier
     * @return list of block responses
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

    /**
     * Deletes a block from the given exam.
     *
     * <p>Validations:</p>
     * <ul>
     *   <li>Block must belong to the given exam.</li>
     *   <li>Block must not have any submissions.</li>
     *   <li>Block must not be within 7 days of its start time.</li>
     * </ul>
     *
     * @param examId  exam identifier
     * @param blockId block identifier
     */
    void deleteBlock(UUID examId, UUID blockId);
}
