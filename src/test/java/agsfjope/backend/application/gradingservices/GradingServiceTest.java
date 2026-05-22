package agsfjope.backend.application.gradingservices;

import agsfjope.backend.application.dtos.requests.grading.TriggerGradingRequest;
import agsfjope.backend.application.dtos.responses.grading.GradingProgressResponse;
import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.enums.GradingMode;
import agsfjope.backend.core.enums.SubmissionStatus;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import agsfjope.backend.testutils.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho GradingService — Orchestrator chấm bài.
 * Phân loại: [N] Normal, [B] Boundary, [A] Abnormal.
 * Pattern: AAA (Arrange - Act - Assert).
 *
 * <p>GradingService không dùng @InjectMocks vì constructor có @Qualifier.
 * Thay vào đó dùng constructor injection thủ công.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GradingService Tests")
class GradingServiceTest {

    @Mock private GradingPipelineService pipelineService;
    @Mock private DeterministicGradingService deterministicGradingService;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private TransactionTemplate transactionTemplate;

    /** Executor đồng bộ — chạy Runnable ngay trên calling thread */
    private final Executor syncExecutor = Runnable::run;

    private GradingService gradingService;

    // ─── Shared fixtures ──────────────────────────────────────────────────────

    private UUID blockId;
    private User staffUser;
    private Block block;
    private Exam exam;

    @BeforeEach
    void setUp() {
        gradingService = new GradingService(
                pipelineService, deterministicGradingService,
                submissionRepository, transactionTemplate, syncExecutor);

        exam = TestDataFactory.createOngoingExam();
        block = TestDataFactory.createOngoingBlock(exam);
        blockId = block.getBlockId();
        staffUser = User.builder()
                .userId(UUID.randomUUID())
                .role(TestDataFactory.createStaffRole())
                .username("staffuser")
                .fullName("Staff User")
                .build();
    }

    // =========================================================================
    // stopGrading()  — [N] 1, [A] 1
    // =========================================================================

    @Test
    @DisplayName("[A] stopGrading - Block không đang chấm → IllegalStateException")
    void stopGrading_BlockNotActive_ThrowsIllegalStateException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        UUID inactiveBlockId = UUID.randomUUID();

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThatThrownBy(() -> gradingService.stopGrading(inactiveBlockId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Quá trình chấm bài hiện không chạy");
    }

    // =========================================================================
    // getProgress()  — [N] 3, [B] 2
    // =========================================================================

    @Test
    @DisplayName("[N] getProgress - Block có submissions đã chấm xong → status=COMPLETED, percent=100")
    void getProgress_AllGraded_ReturnsCompleted100() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(submissionRepository.countByBlock_BlockId(blockId)).thenReturn(5L);
        when(submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADED)).thenReturn(5L);
        when(submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADING)).thenReturn(0L);
        when(submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADING_FAILED)).thenReturn(0L);

        // ── Act ───────────────────────────────────────────────────────────────
        GradingProgressResponse response = gradingService.getProgress(blockId);

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getProgressPercent()).isEqualTo(100);
        assertThat(response.getTotalSubmissions()).isEqualTo(5L);
        assertThat(response.getGradedCount()).isEqualTo(5L);
        assertThat(response.getPendingCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("[N] getProgress - Block đang chấm giữa chừng → status=PENDING, percent tỷ lệ")
    void getProgress_MidGrading_ReturnsPendingWithPercent() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(submissionRepository.countByBlock_BlockId(blockId)).thenReturn(10L);
        when(submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADED)).thenReturn(3L);
        when(submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADING)).thenReturn(0L);
        when(submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADING_FAILED)).thenReturn(0L);

        // ── Act ───────────────────────────────────────────────────────────────
        GradingProgressResponse response = gradingService.getProgress(blockId);

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(response.getProgressPercent()).isEqualTo(30);
        assertThat(response.getGradedCount()).isEqualTo(3L);
        assertThat(response.getPendingCount()).isEqualTo(7L);
    }

    @Test
    @DisplayName("[N] getProgress - Block có submissions failed → đếm failed đúng")
    void getProgress_HasFailedSubmissions_CountsCorrectly() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(submissionRepository.countByBlock_BlockId(blockId)).thenReturn(10L);
        when(submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADED)).thenReturn(6L);
        when(submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADING)).thenReturn(0L);
        when(submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADING_FAILED)).thenReturn(2L);

        // ── Act ───────────────────────────────────────────────────────────────
        GradingProgressResponse response = gradingService.getProgress(blockId);

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(response.getFailedCount()).isEqualTo(2L);
        assertThat(response.getPendingCount()).isEqualTo(2L);
        // percent = (graded + failed) / total * 100 = (6+2)/10*100 = 80
        assertThat(response.getProgressPercent()).isEqualTo(80);
    }

    @Test
    @DisplayName("[B] getProgress - Block không có submission nào → percent=0, status=PENDING")
    void getProgress_NoSubmissions_ReturnsPending0() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(submissionRepository.countByBlock_BlockId(blockId)).thenReturn(0L);
        when(submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADED)).thenReturn(0L);
        when(submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADING)).thenReturn(0L);
        when(submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADING_FAILED)).thenReturn(0L);

        // ── Act ───────────────────────────────────────────────────────────────
        GradingProgressResponse response = gradingService.getProgress(blockId);

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(response.getProgressPercent()).isEqualTo(0);
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getTotalSubmissions()).isEqualTo(0L);
    }

    @Test
    @DisplayName("[B] getProgress - Có 1 submission GRADING đang chạy (không nằm trong activeBlockGradings) → status=IN_PROGRESS")
    void getProgress_OneGrading_ReturnsInProgress() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(submissionRepository.countByBlock_BlockId(blockId)).thenReturn(5L);
        when(submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADED)).thenReturn(2L);
        when(submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADING)).thenReturn(1L);
        when(submissionRepository.countByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADING_FAILED)).thenReturn(0L);

        // ── Act ───────────────────────────────────────────────────────────────
        GradingProgressResponse response = gradingService.getProgress(blockId);

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(response.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(response.getGradingCount()).isEqualTo(1L);
    }

    // =========================================================================
    // isGrading()  — [N] 2
    // =========================================================================

    @Test
    @DisplayName("[N] isGrading - Block không trong activeBlockGradings → false")
    void isGrading_NotActive_ReturnsFalse() {
        // ── Arrange (no active grading) ───────────────────────────────────────

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThat(gradingService.isGrading(blockId)).isFalse();
    }

    @Test
    @DisplayName("[N] isGrading - Block đang active grading → true")
    void isGrading_Active_ReturnsTrue() {
        // ── Arrange ──────────────────────────────────────────────────────────
        // Trigger grading to add blockId to activeBlockGradings
        // We use reflection to add blockId to the internal set
        try {
            var field = GradingService.class.getDeclaredField("activeBlockGradings");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Set<UUID> activeSet = (java.util.Set<UUID>) field.get(gradingService);
            activeSet.add(blockId);
        } catch (Exception e) {
            throw new RuntimeException("Cannot access activeBlockGradings", e);
        }

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThat(gradingService.isGrading(blockId)).isTrue();
    }

    // =========================================================================
    // triggerGrading()  — [N] 1, [A] 1, [B] 1
    // =========================================================================

    @Test
    @DisplayName("[A] triggerGrading - Block đã đang chấm → GradingAlreadyInProgressException")
    void triggerGrading_BlockAlreadyGrading_ThrowsAlreadyInProgress() {
        // ── Arrange ──────────────────────────────────────────────────────────
        // Add blockId to activeBlockGradings via reflection
        try {
            var field = GradingService.class.getDeclaredField("activeBlockGradings");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Set<UUID> activeSet = (java.util.Set<UUID>) field.get(gradingService);
            activeSet.add(blockId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        TriggerGradingRequest request = new TriggerGradingRequest();

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThatThrownBy(() -> gradingService.triggerGrading(blockId, request, staffUser))
                .isInstanceOf(GradingAlreadyInProgressException.class)
                .hasMessageContaining("đang chấm bài");
    }

    @Test
    @DisplayName("[B] triggerGrading - Không có submission nào eligible → log và return không lỗi")
    void triggerGrading_NoEligibleSubmissions_ReturnsWithoutError() {
        // ── Arrange ──────────────────────────────────────────────────────────
        TriggerGradingRequest request = new TriggerGradingRequest(); // submissionIds = null → GRADE_ALL

        // Mock resolveTargets → trả về empty list qua các repo calls
        when(submissionRepository.findByBlock_BlockIdAndStatus(blockId, SubmissionStatus.SUBMITTED))
                .thenReturn(List.of());
        when(submissionRepository.findByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADING))
                .thenReturn(List.of());
        when(submissionRepository.findByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADED))
                .thenReturn(List.of());
        when(submissionRepository.findByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADING_FAILED))
                .thenReturn(List.of());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThatCode(() -> gradingService.triggerGrading(blockId, request, staffUser))
                .doesNotThrowAnyException();

        // Pipeline should NOT be called
        verify(pipelineService, never()).grade(any(), any(), any());
        verify(deterministicGradingService, never()).grade(any(), any(), any());

        // Block should be cleaned up (removed from activeBlockGradings)
        assertThat(gradingService.isGrading(blockId)).isFalse();
    }

    @Test
    @DisplayName("[N] triggerGrading - GRADE_ALL với 1 submission MODE_1 → gọi pipelineService.grade()")
    void triggerGrading_GradeAllMode1_DispatchesToPipeline() {
        // ── Arrange ──────────────────────────────────────────────────────────
        TriggerGradingRequest request = new TriggerGradingRequest(); // submissionIds=null → GRADE_ALL

        Submission sub = TestDataFactory.createSubmission(TestDataFactory.createActiveStudent(), block);
        sub.setStatus(SubmissionStatus.SUBMITTED);

        when(submissionRepository.findByBlock_BlockIdAndStatus(blockId, SubmissionStatus.SUBMITTED))
                .thenReturn(List.of(sub));
        when(submissionRepository.findByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADING))
                .thenReturn(List.of());
        when(submissionRepository.findByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADED))
                .thenReturn(List.of());
        when(submissionRepository.findByBlock_BlockIdAndStatus(blockId, SubmissionStatus.GRADING_FAILED))
                .thenReturn(List.of());

        // Mock transactionTemplate.execute to just run the function
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            var action = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return action.doInTransaction(null);
        });

        // Mock the submission reload inside the TX (findById returns managed entity)
        when(submissionRepository.findById(sub.getSubmissionId()))
                .thenReturn(java.util.Optional.of(sub));

        // ── Act ───────────────────────────────────────────────────────────────
        gradingService.triggerGrading(blockId, request, staffUser);

        // ── Assert ────────────────────────────────────────────────────────────
        // pipelineService.grade() should be called for MODE_1 submission
        verify(pipelineService).grade(eq(sub), eq(staffUser), any());

        // Block should be cleaned up after grading completes
        assertThat(gradingService.isGrading(blockId)).isFalse();
    }
}
