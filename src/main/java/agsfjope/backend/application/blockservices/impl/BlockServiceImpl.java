package agsfjope.backend.application.blockservices.impl;

import agsfjope.backend.application.blockservices.BlockService;
import agsfjope.backend.application.dtos.requests.block.CreateBlockRequest;
import agsfjope.backend.application.dtos.requests.block.UpdateBlockRequest;
import agsfjope.backend.application.dtos.responses.block.BlockResponse;
import agsfjope.backend.core.entities.Block;
import agsfjope.backend.core.entities.Exam;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.repositories.block.BlockRepository;
import agsfjope.backend.core.repositories.exam.ExamRepository;
import agsfjope.backend.core.repositories.exampaper.ExamPaperRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link BlockService}.
 *
 * <p>Business rules enforced:</p>
 * <ul>
 *   <li>Blocks are created manually by Exam Staff with custom names (unique within exam).</li>
 *   <li>Block.StartTime and Block.EndTime must fall within Exam.StartTime — Exam.EndTime.</li>
 *   <li>Block.EndTime must be strictly after Block.StartTime (also enforced by CHK_BlockTime).</li>
 *   <li>Blocks cannot be deleted if they have submissions or are within 7 days of start.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockServiceImpl implements BlockService {

    private static final String BLOCK_10 = "Block 10";
    private static final String BLOCK_3  = "Block 3";

    private final BlockRepository      blockRepository;
    private final ExamRepository       examRepository;
    private final ExamPaperRepository  examPaperRepository;
    private final SubmissionRepository submissionRepository;

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

        // Chỉ tạo block với tên — ngày/giờ thi để null, Exam Staff sẽ cập nhật sau
        Block block10 = Block.builder()
                .exam(exam)
                .name(BLOCK_10)
                .build();

        Block block3 = Block.builder()
                .exam(exam)
                .name(BLOCK_3)
                .build();

        blockRepository.save(block10);
        blockRepository.save(block3);

        log.info("Auto-created Block 10 + Block 3 for exam '{}'", exam.getName());
    }

    // ─── CREATE ──────────────────────────────────────────────────────────

    /**
     * Creates a new block within the given exam.
     * Validates exam existence and name uniqueness.
     */
    @Override
    @Transactional
    public BlockResponse createBlock(UUID examId, CreateBlockRequest request) {
        Exam exam = examRepository.findByExamIdAndDeletedAtIsNull(examId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy kỳ thi với ID: " + examId));

        // Validate block name is unique within this exam
        if (blockRepository.existsByExam_ExamIdAndName(examId, request.getName().trim())) {
            throw new IllegalArgumentException(
                    "Tên đợt thi \"" + request.getName().trim() + "\" đã tồn tại trong kỳ thi này."
            );
        }

        Block block = Block.builder()
                .exam(exam)
                .name(request.getName().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .build();

        blockRepository.save(block);
        log.info("Created block '{}' for exam '{}'", block.getName(), exam.getName());
        return mapToResponse(block);
    }

    // ─── READ ─────────────────────────────────────────────────────────────

    /**
     * Returns all blocks for the given exam, ordered by name.
     */
    @Override
    @Transactional
    public List<BlockResponse> getBlocksByExamId(UUID examId) {
        Exam exam = examRepository.findByExamIdAndDeletedAtIsNull(examId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy kỳ thi với ID: " + examId));

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

        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        // If block already passed, use a dedicated message
        if (block.getEndTime() != null && now.isAfter(block.getEndTime())) {
            throw new IllegalArgumentException("Kì thi đã qua không được chỉnh sửa thời gian ca thi");
        }

        // Otherwise, block update is locked in the 7-day window before start time
        if (block.getStartTime() != null) {
            OffsetDateTime lockAt = block.getStartTime().minusDays(7);
            if (!now.isBefore(lockAt)) {
                throw new IllegalArgumentException("Không thể chỉnh sửa lịch trong vòng 7 ngày trước khi ca thi bắt đầu.");
            }
        }

        if (!request.getStartTime().isAfter(now)) {
            throw new IllegalArgumentException("Nếu chọn ngày hôm nay, thời gian bắt đầu phải lớn hơn thời điểm hiện tại.");
        }

        if (!request.getEndTime().isAfter(now)) {
            throw new IllegalArgumentException("Nếu chọn ngày hôm nay, thời gian kết thúc phải lớn hơn thời điểm hiện tại.");
        }

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

    // ─── DELETE ───────────────────────────────────────────────────────────

    /**
     * Deletes a block. Validates:
     * 1. Block belongs to the given exam.
     * 2. Block has no submissions.
     * 3. Block is not within 7 days of its start time (if scheduled).
     */
    @Override
    @Transactional
    public void deleteBlock(UUID examId, UUID blockId) {
        Block block = blockRepository.findByBlockId(blockId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đợt thi với ID: " + blockId));

        if (!block.getExam().getExamId().equals(examId)) {
            throw new IllegalArgumentException(
                    "Đợt thi " + blockId + " không thuộc kỳ thi " + examId + "."
            );
        }

        // Cannot delete if block has submissions
        if (submissionRepository.existsByBlock_BlockId(blockId)) {
            throw new IllegalArgumentException(
                    "Không thể xóa đợt thi \"" + block.getName() + "\" vì đã có bài nộp."
            );
        }

        // Cannot delete if block is within 7 days of start
        if (block.getStartTime() != null) {
            OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
            OffsetDateTime lockAt = block.getStartTime().minusDays(7);
            if (!now.isBefore(lockAt)) {
                throw new IllegalArgumentException(
                        "Không thể xóa đợt thi \"" + block.getName() + "\" trong vòng 7 ngày trước khi bắt đầu."
                );
            }
        }

        blockRepository.delete(block);
        log.info("Deleted block '{}' from exam '{}'", block.getName(), block.getExam().getName());
    }

    // ─── MAPPING ──────────────────────────────────────────────────────────

    private BlockResponse mapToResponse(Block block) {
        boolean hasPaper = block.getBlockId() != null
                && examPaperRepository.existsByBlock_BlockId(block.getBlockId());
        return BlockResponse.builder()
                .blockId(block.getBlockId())
                .examId(block.getExam().getExamId())
                .name(block.getName())
                .description(block.getDescription())
                .examDate(block.getExamDate())
                .startTime(block.getStartTime())
                .endTime(block.getEndTime())
                .createdAt(block.getCreatedAt())
                .hasPaper(hasPaper)
                .build();
    }
}
