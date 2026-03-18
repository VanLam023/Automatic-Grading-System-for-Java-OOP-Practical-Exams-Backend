package agsfjope.backend.application.blockservices.impl;

import agsfjope.backend.application.blockservices.BlockService;
import agsfjope.backend.application.dtos.requests.block.UpdateBlockRequest;
import agsfjope.backend.application.dtos.responses.block.BlockResponse;
import agsfjope.backend.core.entities.Block;
import agsfjope.backend.core.entities.Exam;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.repositories.block.BlockRepository;
import agsfjope.backend.core.repositories.exam.ExamRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link BlockService}.
 *
 * <p>Business rules enforced:</p>
 * <ul>
 *   <li>BR-08: Exactly 2 blocks per exam (Block 10 + Block 3). Created automatically.</li>
 *   <li>Block.StartTime and Block.EndTime must fall within Exam.StartTime — Exam.EndTime.</li>
 *   <li>Block.EndTime must be strictly after Block.StartTime (also enforced by CHK_BlockTime).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockServiceImpl implements BlockService {

    private static final String BLOCK_10 = "Block 10";
    private static final String BLOCK_3  = "Block 3";

    private final BlockRepository blockRepository;
    private final ExamRepository  examRepository;

    // ─── AUTO-CREATE ─────────────────────────────────────────────────────

    /**
     * Creates Block 10 and Block 3 for the given exam.
     * Default schedule: exam's StartTime date, time window = exam's start to end.
     * Exam Staff must update the actual block times afterward.
     *
     * @param exam the newly saved exam
     */
    @Override
    @Transactional
    public void createDefaultBlocks(Exam exam) {
        if (blockRepository.existsByExam_ExamId(exam.getExamId())) {
            log.warn("Blocks already exist for exam {} — skipping auto-create.", exam.getExamId());
            return;
        }

        LocalDate defaultDate = exam.getStartTime().toLocalDate();

        Block block10 = Block.builder()
                .exam(exam)
                .name(BLOCK_10)
                .examDate(defaultDate)
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .build();

        Block block3 = Block.builder()
                .exam(exam)
                .name(BLOCK_3)
                .examDate(defaultDate)
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .build();

        blockRepository.save(block10);
        blockRepository.save(block3);

        log.info("Auto-created Block 10 + Block 3 for exam '{}'", exam.getName());
    }

    // ─── READ ─────────────────────────────────────────────────────────────

    /**
     * Returns both blocks for the given exam, ordered by name (Block 10, Block 3).
     */
    @Override
    public List<BlockResponse> getBlocksByExamId(UUID examId) {
        if (!examRepository.existsByExamIdAndDeletedAtIsNull(examId)) {
            throw new NotFoundException("Không tìm thấy kỳ thi với ID: " + examId);
        }
        return blockRepository.findByExam_ExamIdOrderByNameAsc(examId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Returns a single block by its ID, validating it belongs to the given exam.
     */
    @Override
    public BlockResponse getBlockById(UUID examId, UUID blockId) {
        Block block = blockRepository.findByBlockId(blockId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy block với ID: " + blockId));
        if (!block.getExam().getExamId().equals(examId)) {
            throw new IllegalArgumentException(
                    "Block " + blockId + " không thuộc kỳ thi " + examId + "."
            );
        }
        return mapToResponse(block);
    }

    // ─── UPDATE ───────────────────────────────────────────────────────────

    /**
     * Updates block schedule. Validates:
     * <ol>
     *   <li>Block belongs to the given exam.</li>
     *   <li>Block.StartTime + EndTime fall within Exam.StartTime — Exam.EndTime.</li>
     * </ol>
     */
    @Override
    @Transactional
    public BlockResponse updateBlock(UUID examId, UUID blockId, UpdateBlockRequest request) {
        Block block = blockRepository.findByBlockId(blockId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy block với ID: " + blockId));

        // Ownership check — block must belong to stated exam
        if (!block.getExam().getExamId().equals(examId)) {
            throw new IllegalArgumentException(
                    "Block " + blockId + " không thuộc kỳ thi " + examId + "."
            );
        }

        Exam exam = block.getExam();

        // Validate block times fall within the parent exam's window
        if (request.getStartTime().isBefore(exam.getStartTime())) {
            throw new IllegalArgumentException(
                    "Thời gian bắt đầu của block phải sau hoặc bằng thời gian bắt đầu kỳ thi ("
                    + exam.getStartTime() + ")."
            );
        }
        if (request.getEndTime().isAfter(exam.getEndTime())) {
            throw new IllegalArgumentException(
                    "Thời gian kết thúc của block phải trước hoặc bằng thời gian kết thúc kỳ thi ("
                    + exam.getEndTime() + ")."
            );
        }

        // Apply updates
        block.setExamDate(request.getExamDate());
        block.setStartTime(request.getStartTime());
        block.setEndTime(request.getEndTime());

        blockRepository.save(block);
        log.info("Updated schedule for {} of exam '{}'", block.getName(), exam.getName());
        return mapToResponse(block);
    }

    // ─── MAPPING ──────────────────────────────────────────────────────────

    private BlockResponse mapToResponse(Block block) {
        return BlockResponse.builder()
                .blockId(block.getBlockId())
                .examId(block.getExam().getExamId())
                .name(block.getName())
                .description(block.getDescription())
                .examDate(block.getExamDate())
                .startTime(block.getStartTime())
                .endTime(block.getEndTime())
                .createdAt(block.getCreatedAt())
                .build();
    }
}
