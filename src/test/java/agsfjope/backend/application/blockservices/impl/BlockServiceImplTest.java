package agsfjope.backend.application.blockservices.impl;

import agsfjope.backend.application.dtos.requests.block.UpdateBlockRequest;
import agsfjope.backend.application.dtos.responses.block.BlockResponse;
import agsfjope.backend.core.entities.Block;
import agsfjope.backend.core.entities.Exam;
import agsfjope.backend.core.enums.GradingMode;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.repositories.block.BlockRepository;
import agsfjope.backend.core.repositories.exam.ExamRepository;
import agsfjope.backend.core.repositories.exampaper.ExamPaperRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests cho BlockServiceImpl — 14 test cases (N/A/B).
 * Pattern: AAA (Arrange - Act - Assert)
 * Tên method: methodName_Condition_ExpectedBehavior
 *
 * <p>Business rules được kiểm tra:
 * - BR-08: Đúng 2 blocks (Block 10 + Block 3) per exam, auto-create.
 * - Block.StartTime/EndTime phải nằm trong [Exam.StartTime, Exam.EndTime].
 * - Không chỉnh sửa block đã qua (endTime < now).
 * - Không chỉnh sửa block trong vòng 7 ngày trước startTime.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class BlockServiceImplTest {

    @Mock private BlockRepository    blockRepository;
    @Mock private ExamRepository     examRepository;
    @Mock private ExamPaperRepository examPaperRepository;

    @InjectMocks
    private BlockServiceImpl blockService;

    // ─── constants & helpers ──────────────────────────────────────────────────

    private static final ZoneOffset VN = ZoneOffset.ofHours(7);

    private Exam buildExam() {
        return Exam.builder()
                .examId(UUID.randomUUID())
                .name("Midterm SP2026")
                .semester("SP2026")
                .academicYear("2026")
                .startTime(OffsetDateTime.now(VN).minusDays(10))
                .endTime  (OffsetDateTime.now(VN).plusDays(30))
                .gradingMode(GradingMode.MODE_1)
                .build();
    }

    private Block buildBlock(Exam exam, String name) {
        return Block.builder()
                .blockId(UUID.randomUUID())
                .exam(exam)
                .name(name)
                .startTime(null) // chưa lên lịch
                .endTime(null)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private UpdateBlockRequest validUpdateRequest(Exam exam) {
        UpdateBlockRequest req = new UpdateBlockRequest();
        // Thời gian hợp lệ: nằm trong [exam.startTime, exam.endTime], không bị locked
        req.setExamDate(LocalDate.now(VN).plusDays(10));
        req.setStartTime(OffsetDateTime.now(VN).plusDays(10).withHour(8));
        req.setEndTime  (OffsetDateTime.now(VN).plusDays(10).withHour(11));
        return req;
    }

    // =========================================================================
    // createDefaultBlocks()  — [N] 1 normal, [B] 1 boundary (already exists)
    // =========================================================================

    @Test
    @DisplayName("[N] createDefaultBlocks - Exam chưa có block → tạo Block 10 + Block 3 thành công")
    void createDefaultBlocks_NoBlocksYet_SavesTwoBlocks() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Exam exam = buildExam();
        when(blockRepository.existsByExam_ExamId(exam.getExamId())).thenReturn(false);
        when(blockRepository.save(any(Block.class))).thenAnswer(inv -> inv.getArgument(0));

        // ── Act ───────────────────────────────────────────────────────────────
        blockService.createDefaultBlocks(exam);

        // ── Assert ────────────────────────────────────────────────────────────
        ArgumentCaptor<Block> captor = ArgumentCaptor.forClass(Block.class);
        verify(blockRepository, times(2)).save(captor.capture());

        List<Block> saved = captor.getAllValues();
        List<String> names = saved.stream().map(Block::getName).toList();
        assertTrue(names.contains("Block 10"), "Phải tạo Block 10");
        assertTrue(names.contains("Block 3"),  "Phải tạo Block 3");
        saved.forEach(b -> assertEquals(exam, b.getExam(), "Block phải thuộc exam vừa tạo"));
    }

    @Test
    @DisplayName("[B] createDefaultBlocks - Block đã tồn tại trước đó → bỏ qua, không save thêm")
    void createDefaultBlocks_BlocksAlreadyExist_SkipsCreation() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Exam exam = buildExam();
        when(blockRepository.existsByExam_ExamId(exam.getExamId())).thenReturn(true);

        // ── Act ───────────────────────────────────────────────────────────────
        blockService.createDefaultBlocks(exam);

        // ── Assert ────────────────────────────────────────────────────────────
        verify(blockRepository, never()).save(any());
    }

    // =========================================================================
    // getBlocksByExamId()  — [N] 1 normal, [A] 1 abnormal, [B] 1 boundary (no blocks → auto-create)
    // =========================================================================

    @Test
    @DisplayName("[N] getBlocksByExamId - Exam tồn tại, đã có 2 blocks → trả về List 2 BlockResponse")
    void getBlocksByExamId_ExamWithBlocks_ReturnsTwoResponses() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Exam exam = buildExam();
        Block b10 = buildBlock(exam, "Block 10");
        Block b3  = buildBlock(exam, "Block 3");

        when(examRepository.findByExamIdAndDeletedAtIsNull(exam.getExamId())).thenReturn(Optional.of(exam));
        when(blockRepository.existsByExam_ExamId(exam.getExamId())).thenReturn(true);
        when(blockRepository.findByExam_ExamIdOrderByNameAsc(exam.getExamId())).thenReturn(List.of(b10, b3));
        when(examPaperRepository.existsByBlock_BlockId(any())).thenReturn(false);

        // ── Act ───────────────────────────────────────────────────────────────
        List<BlockResponse> result = blockService.getBlocksByExamId(exam.getExamId());

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(blockRepository, never()).save(any()); // không auto-create lại
    }

    @Test
    @DisplayName("[A] getBlocksByExamId - ExamId không tồn tại → NotFoundException")
    void getBlocksByExamId_ExamNotFound_ThrowsNotFoundException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        UUID badId = UUID.randomUUID();
        when(examRepository.findByExamIdAndDeletedAtIsNull(badId)).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(NotFoundException.class, () -> blockService.getBlocksByExamId(badId));
    }

    @Test
    @DisplayName("[B] getBlocksByExamId - Exam tồn tại nhưng chưa có block → auto-create rồi trả về")
    void getBlocksByExamId_ExamWithNoBlocks_AutoCreatesAndReturns() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Exam exam = buildExam();
        Block b10 = buildBlock(exam, "Block 10");
        Block b3  = buildBlock(exam, "Block 3");

        when(examRepository.findByExamIdAndDeletedAtIsNull(exam.getExamId())).thenReturn(Optional.of(exam));
        // Lần gọi thứ nhất: existsByExam (trong getBlocksByExamId) → false → trigger auto-create
        // Lần gọi thứ hai: existsByExam (trong createDefaultBlocks) → false → thực sự save
        when(blockRepository.existsByExam_ExamId(exam.getExamId())).thenReturn(false);
        when(blockRepository.save(any(Block.class))).thenAnswer(inv -> inv.getArgument(0));
        when(blockRepository.findByExam_ExamIdOrderByNameAsc(exam.getExamId())).thenReturn(List.of(b10, b3));
        when(examPaperRepository.existsByBlock_BlockId(any())).thenReturn(false);

        // ── Act ───────────────────────────────────────────────────────────────
        List<BlockResponse> result = blockService.getBlocksByExamId(exam.getExamId());

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(result);
        // auto-create lưu 2 block
        verify(blockRepository, times(2)).save(any(Block.class));
    }

    // =========================================================================
    // getBlockById()  — [N] 1 normal, [A] 2 abnormal
    // =========================================================================

    @Test
    @DisplayName("[N] getBlockById - Block tồn tại và thuộc đúng exam → trả về BlockResponse")
    void getBlockById_ValidOwnership_ReturnsResponse() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Exam exam = buildExam();
        Block block = buildBlock(exam, "Block 10");

        when(blockRepository.findByBlockId(block.getBlockId())).thenReturn(Optional.of(block));
        when(examPaperRepository.existsByBlock_BlockId(block.getBlockId())).thenReturn(false);

        // ── Act ───────────────────────────────────────────────────────────────
        BlockResponse response = blockService.getBlockById(exam.getExamId(), block.getBlockId());

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals(block.getBlockId(), response.getBlockId());
        assertEquals("Block 10", response.getName());
    }

    @Test
    @DisplayName("[A] getBlockById - BlockId không tồn tại → NotFoundException")
    void getBlockById_BlockNotFound_ThrowsNotFoundException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        UUID badBlockId = UUID.randomUUID();
        when(blockRepository.findByBlockId(badBlockId)).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(NotFoundException.class,
                () -> blockService.getBlockById(UUID.randomUUID(), badBlockId));
    }

    @Test
    @DisplayName("[A] getBlockById - Block tồn tại nhưng thuộc exam khác → IllegalArgumentException")
    void getBlockById_BlockBelongsToDifferentExam_ThrowsIllegalArgumentException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Exam exam = buildExam();        // exam thật
        UUID wrongExamId = UUID.randomUUID(); // exam giả được truyền vào

        Block block = buildBlock(exam, "Block 3");
        when(blockRepository.findByBlockId(block.getBlockId())).thenReturn(Optional.of(block));

        // ── Act & Assert ──────────────────────────────────────────────────────
        // examId truyền vào khác với block.getExam().getExamId()
        assertThrows(IllegalArgumentException.class,
                () -> blockService.getBlockById(wrongExamId, block.getBlockId()));
    }

    // =========================================================================
    // updateBlock()  — [N] 1 normal, [A] 4 abnormal, [B] 1 boundary
    // =========================================================================

    @Test
    @DisplayName("[N] updateBlock - Thời gian hợp lệ, chưa lock → cập nhật thành công")
    void updateBlock_ValidRequest_UpdatesSuccessfully() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Exam exam = buildExam(); // startTime = 2026-03-01, endTime = 2026-04-30
        Block block = buildBlock(exam, "Block 10");
        // Block chưa có startTime → không bị lock

        when(blockRepository.findByBlockId(block.getBlockId())).thenReturn(Optional.of(block));
        when(blockRepository.save(any(Block.class))).thenAnswer(inv -> inv.getArgument(0));
        when(examPaperRepository.existsByBlock_BlockId(block.getBlockId())).thenReturn(false);

        UpdateBlockRequest request = validUpdateRequest(exam);

        // ── Act ───────────────────────────────────────────────────────────────
        BlockResponse response = blockService.updateBlock(exam.getExamId(), block.getBlockId(), request);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals(request.getStartTime(), response.getStartTime());
        assertEquals(request.getEndTime(),   response.getEndTime());
        verify(blockRepository).save(block);
    }


    @Test
    @DisplayName("[A] updateBlock - StartTime không lớn hơn hiện tại → IllegalArgumentException")
    void updateBlock_StartTimeNotAfterNow_ThrowsIllegalArgumentException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Exam exam = Exam.builder()
                .examId(UUID.randomUUID())
                .name("Realtime Exam")
                .semester("SP2026")
                .academicYear("2025-2026")
                .startTime(OffsetDateTime.now(VN).minusDays(1))
                .endTime(OffsetDateTime.now(VN).plusDays(1))
                .gradingMode(GradingMode.MODE_1)
                .build();
        Block block = buildBlock(exam, "Block 10");

        when(blockRepository.findByBlockId(block.getBlockId())).thenReturn(Optional.of(block));

        UpdateBlockRequest request = new UpdateBlockRequest();
        request.setExamDate(LocalDate.now(VN));
        request.setStartTime(OffsetDateTime.now(VN).minusMinutes(1));
        request.setEndTime(OffsetDateTime.now(VN).plusMinutes(10));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(IllegalArgumentException.class,
                () -> blockService.updateBlock(exam.getExamId(), block.getBlockId(), request));
    }

    @Test
    @DisplayName("[A] updateBlock - Block ID không tồn tại → NotFoundException")
    void updateBlock_BlockNotFound_ThrowsNotFoundException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        UUID badId = UUID.randomUUID();
        when(blockRepository.findByBlockId(badId)).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(NotFoundException.class,
                () -> blockService.updateBlock(UUID.randomUUID(), badId, new UpdateBlockRequest()));
    }

    @Test
    @DisplayName("[A] updateBlock - Block thuộc exam khác → IllegalArgumentException (ownership)")
    void updateBlock_WrongExamId_ThrowsIllegalArgumentException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Exam exam = buildExam();
        Block block = buildBlock(exam, "Block 10");
        UUID wrongExamId = UUID.randomUUID();

        when(blockRepository.findByBlockId(block.getBlockId())).thenReturn(Optional.of(block));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(IllegalArgumentException.class,
                () -> blockService.updateBlock(wrongExamId, block.getBlockId(), new UpdateBlockRequest()));
    }

    @Test
    @DisplayName("[A] updateBlock - Block đã qua (endTime trong quá khứ) → IllegalArgumentException")
    void updateBlock_BlockAlreadyEnded_ThrowsIllegalArgumentException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Exam exam = buildExam();
        Block block = buildBlock(exam, "Block 10");
        // Đặt endTime trong quá khứ
        block.setEndTime(OffsetDateTime.now().minusDays(1));
        block.setStartTime(OffsetDateTime.now().minusDays(2));

        when(blockRepository.findByBlockId(block.getBlockId())).thenReturn(Optional.of(block));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(IllegalArgumentException.class,
                () -> blockService.updateBlock(exam.getExamId(), block.getBlockId(), validUpdateRequest(exam)));
    }

    @Test
    @DisplayName("[A] updateBlock - Còn trong vòng 7 ngày trước startTime → IllegalArgumentException (locked)")
    void updateBlock_Within7DaysBeforeStart_ThrowsIllegalArgumentException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Exam exam = buildExam();
        Block block = buildBlock(exam, "Block 10");
        // startTime = 3 ngày sau → now >= startTime - 7 → LOCK
        block.setStartTime(OffsetDateTime.now().plusDays(3));
        block.setEndTime(OffsetDateTime.now().plusDays(4)); // endTime chưa qua

        when(blockRepository.findByBlockId(block.getBlockId())).thenReturn(Optional.of(block));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(IllegalArgumentException.class,
                () -> blockService.updateBlock(exam.getExamId(), block.getBlockId(), validUpdateRequest(exam)));
    }

    @Test
    @DisplayName("[B] updateBlock - Block.StartTime request trước Exam.StartTime → IllegalArgumentException")
    void updateBlock_RequestStartTimeBeforeExamStart_ThrowsIllegalArgumentException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Exam exam = buildExam(); // exam.startTime = 2026-03-01
        Block block = buildBlock(exam, "Block 10");
        // endTime null, startTime null → chưa lên lịch → không trigger lock/passed checks

        when(blockRepository.findByBlockId(block.getBlockId())).thenReturn(Optional.of(block));

        UpdateBlockRequest request = new UpdateBlockRequest();
        request.setExamDate(LocalDate.now(VN).minusDays(20));
        // startTime TRƯỚC exam.startTime (now - 10 ngày) → phải bị reject
        request.setStartTime(OffsetDateTime.now(VN).minusDays(20).withHour(8));
        request.setEndTime  (OffsetDateTime.now(VN).minusDays(20).withHour(11));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(IllegalArgumentException.class,
                () -> blockService.updateBlock(exam.getExamId(), block.getBlockId(), request));
    }
}
