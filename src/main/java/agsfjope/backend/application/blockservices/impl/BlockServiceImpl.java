package agsfjope.backend.application.blockservices.impl;

import agsfjope.backend.application.blockservices.BlockService;
import agsfjope.backend.application.dtos.requests.block.UpdateBlockRequest;
import agsfjope.backend.application.dtos.responses.block.BlockResponse;
import agsfjope.backend.core.entities.Block;
import agsfjope.backend.core.entities.Exam;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.repositories.block.BlockRepository;
import agsfjope.backend.core.repositories.exam.ExamRepository;
import agsfjope.backend.core.repositories.exampaper.ExamPaperRepository;
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

    private final BlockRepository    blockRepository;
    private final ExamRepository     examRepository;
    private final ExamPaperRepository examPaperRepository;

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

    // ─── READ ─────────────────────────────────────────────────────────────

    /**
     * Returns both blocks for the given exam, ordered by name (Block 10, Block 3).
     */
    @Override
    @Transactional
    public List<BlockResponse> getBlocksByExamId(UUID examId) {
        Exam exam = examRepository.findByExamIdAndDeletedAtIsNull(examId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy kỳ thi với ID: " + examId));

        // BR-08: Nếu exam chưa có block (exam tạo trước khi có logic auto-create),
        // tự tạo Block 10 + Block 3 ngay lúc này để đảm bảo tính nhất quán.
        if (!blockRepository.existsByExam_ExamId(examId)) {
            log.warn("Exam '{}' has no blocks yet — auto-creating Block 10 + Block 3.", exam.getName());
            createDefaultBlocks(exam);
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
