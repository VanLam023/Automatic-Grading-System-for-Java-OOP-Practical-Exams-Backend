package agsfjope.backend.application.examservices.impl;

import agsfjope.backend.application.blockservices.BlockService;
import agsfjope.backend.application.dtos.requests.exam.CreateExamRequest;
import agsfjope.backend.application.dtos.requests.exam.UpdateExamRequest;
import agsfjope.backend.application.dtos.responses.exam.ExamResponse;
import agsfjope.backend.core.entities.Exam;
import agsfjope.backend.core.entities.SystemConfig;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.GradingMode;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.exceptions.exam.ExamConflictException;
import agsfjope.backend.core.repositories.block.BlockRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.core.repositories.exam.ExamRepository;
import agsfjope.backend.infrastructure.security.CustomUserDetails;
import agsfjope.backend.testutils.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests cho ExamServiceImpl — 20+ test cases (N/A/B).
 * Pattern: AAA (Arrange - Act - Assert)
 * Tên method: methodName_Condition_ExpectedBehavior
 *
 * <p>Ghi chú setup SecurityContext: ExamServiceImpl.createExam() gọi
 * SecurityUtils.getCurrentUser() → đọc từ SecurityContextHolder.
 * Trong các test liên quan, ta tự set Authentication vào Context.</p>
 */
@ExtendWith(MockitoExtension.class)
class ExamServiceImplTest {

    @Mock private ExamRepository examRepository;
    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private BlockRepository blockRepository;
    @Mock private BlockService blockService;

    @InjectMocks
    private ExamServiceImpl examService;

    /** Inject @Lazy @Autowired blockService thủ công vì @InjectMocks không xử lý được */
    @BeforeEach
    void injectLazyDependencies() {
        ReflectionTestUtils.setField(examService, "blockService", blockService);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    /** Tạo năm hiện tại (2026) để dùng chung */
    private static final int CURRENT_YEAR = 2026;
    private static final ZoneOffset VN = ZoneOffset.ofHours(7);

    /** startTime hợp lệ: trong năm 2026, cách nhau 30 ngày */
    private OffsetDateTime validStart() {
        return OffsetDateTime.of(CURRENT_YEAR, 3, 1, 8, 0, 0, 0, VN);
    }

    private OffsetDateTime validEnd() {
        return OffsetDateTime.of(CURRENT_YEAR, 4, 1, 20, 0, 0, 0, VN);
    }

    /** Đưa User vào Spring Security Context để SecurityUtils.getCurrentUser() hoạt động */
    private void setupSecurityContext(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** Tạo Exam entity mẫu để làm giả DB */
    private Exam buildExamEntity(String semester) {
        return Exam.builder()
                .examId(UUID.randomUUID())
                .name("Kỳ thi " + semester)
                .semester(semester)
                .academicYear(String.valueOf(CURRENT_YEAR))
                .startTime(validStart())
                .endTime(validEnd())
                .gradingMode(GradingMode.MODE_1)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    /** Xóa SecurityContext sau mỗi test để tránh ảnh hưởng chéo */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // createExam()  — [N] 1 normal, [A] 3 abnormal, [B] 1 boundary
    // =========================================================================

    @Test
    @DisplayName("[N] createExam - Tạo kỳ thi thành công với đầy đủ thông tin hợp lệ")
    void createExam_ValidRequest_ReturnsExamResponse() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User admin = TestDataFactory.createActiveStudent();
        setupSecurityContext(admin);

        CreateExamRequest request = new CreateExamRequest();
        request.setName("Midterm 2026");
        request.setSemester("SP2026");
        request.setDescription("Kỳ thi giữa kỳ SP2026");
        request.setStartTime(validStart());
        request.setEndTime(validEnd());

        SystemConfig gradingConfig = TestDataFactory.createPlainConfig("DEFAULT_GRADING_MODE", "MODE_1");

        when(examRepository.existsBySemesterAndDeletedAtIsNull("SP2026")).thenReturn(false);
        when(systemConfigRepository.findByConfigKey("DEFAULT_GRADING_MODE")).thenReturn(Optional.of(gradingConfig));
        when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(blockService).createDefaultBlocks(any(Exam.class));

        // ── Act ───────────────────────────────────────────────────────────────
        ExamResponse response = examService.createExam(request);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals("Midterm 2026", response.getName());
        assertEquals("SP2026", response.getSemester());
        assertEquals(String.valueOf(CURRENT_YEAR), response.getAcademicYear());
        assertEquals(GradingMode.MODE_1, response.getGradingMode());
        verify(examRepository).save(any(Exam.class));
        verify(blockService).createDefaultBlocks(any(Exam.class));
    }

    @Test
    @DisplayName("[A] createExam - Semester đã tồn tại trong DB → IllegalArgumentException")
    void createExam_DuplicateSemester_ThrowsIllegalArgumentException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User admin = TestDataFactory.createActiveStudent();
        setupSecurityContext(admin);

        CreateExamRequest request = new CreateExamRequest();
        request.setName("Dup Exam");
        request.setSemester("SP2026");
        request.setStartTime(validStart());
        request.setEndTime(validEnd());

        when(examRepository.existsBySemesterAndDeletedAtIsNull("SP2026")).thenReturn(true);

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(IllegalArgumentException.class, () -> examService.createExam(request));
        verify(examRepository, never()).save(any());
        verify(blockService, never()).createDefaultBlocks(any());
    }

    @Test
    @DisplayName("[A] createExam - StartTime nằm ngoài năm học hiện tại → IllegalArgumentException")
    void createExam_StartTimeOutsideAcademicYear_ThrowsIllegalArgumentException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User admin = TestDataFactory.createActiveStudent();
        setupSecurityContext(admin);

        CreateExamRequest request = new CreateExamRequest();
        request.setName("Bad Start");
        request.setSemester("SP2026");
        // startTime ở năm 2025 — ngoài academic year 2026
        request.setStartTime(OffsetDateTime.of(2025, 6, 1, 8, 0, 0, 0, VN));
        request.setEndTime(validEnd());

        when(examRepository.existsBySemesterAndDeletedAtIsNull("SP2026")).thenReturn(false);

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(IllegalArgumentException.class, () -> examService.createExam(request));
        verify(examRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] createExam - DEFAULT_GRADING_MODE chưa cấu hình trong SystemConfig → IllegalStateException")
    void createExam_MissingGradingModeConfig_ThrowsIllegalStateException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User admin = TestDataFactory.createActiveStudent();
        setupSecurityContext(admin);

        CreateExamRequest request = new CreateExamRequest();
        request.setName("No Grading Mode");
        request.setSemester("SU2026");
        request.setStartTime(validStart());
        request.setEndTime(validEnd());

        when(examRepository.existsBySemesterAndDeletedAtIsNull("SU2026")).thenReturn(false);
        when(systemConfigRepository.findByConfigKey("DEFAULT_GRADING_MODE")).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(IllegalStateException.class, () -> examService.createExam(request));
    }

    @Test
    @DisplayName("[B] createExam - Duration đúng 135 ngày (giới hạn trên) → thành công")
    void createExam_ExactlyMaxDuration_Succeeds() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User admin = TestDataFactory.createActiveStudent();
        setupSecurityContext(admin);

        // Jan 1 → May 16 = 135 ngày trong năm 2026
        OffsetDateTime start = OffsetDateTime.of(CURRENT_YEAR, 1, 1, 0, 0, 0, 0, VN);
        OffsetDateTime end   = OffsetDateTime.of(CURRENT_YEAR, 5, 16, 0, 0, 0, 0, VN);

        CreateExamRequest request = new CreateExamRequest();
        request.setName("Max Duration Exam");
        request.setSemester("SP2026");
        request.setStartTime(start);
        request.setEndTime(end);

        SystemConfig gradingConfig = TestDataFactory.createPlainConfig("DEFAULT_GRADING_MODE", "MODE_1");
        when(examRepository.existsBySemesterAndDeletedAtIsNull("SP2026")).thenReturn(false);
        when(systemConfigRepository.findByConfigKey("DEFAULT_GRADING_MODE")).thenReturn(Optional.of(gradingConfig));
        when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(blockService).createDefaultBlocks(any(Exam.class));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertDoesNotThrow(() -> examService.createExam(request));
        verify(examRepository).save(any(Exam.class));
    }

    // =========================================================================
    // getAllExams()  — [N] 1 normal (list), [A] 1 abnormal (list rỗng)
    // =========================================================================

    @Test
    @DisplayName("[N] getAllExams - Trả về danh sách tất cả kỳ thi còn hoạt động")
    void getAllExams_TwoExist_ReturnsBothMapped() {
        // ── Arrange ──────────────────────────────────────────────────────────
        List<Exam> exams = List.of(buildExamEntity("SP2026"), buildExamEntity("SU2026"));
        when(examRepository.findAllByDeletedAtIsNull()).thenReturn(exams);

        // ── Act ───────────────────────────────────────────────────────────────
        List<ExamResponse> result = examService.getAllExams();

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("[A] getAllExams - Không có kỳ thi nào trong DB → trả về danh sách rỗng")
    void getAllExams_NoneExist_ReturnsEmptyList() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(examRepository.findAllByDeletedAtIsNull()).thenReturn(List.of());

        // ── Act ───────────────────────────────────────────────────────────────
        List<ExamResponse> result = examService.getAllExams();

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // =========================================================================
    // getAllExams(Pageable)  — [N] 1 normal
    // =========================================================================

    @Test
    @DisplayName("[N] getAllExams(Pageable) - Trả về Page<ExamResponse> phân trang đúng")
    void getAllExams_Pageable_ReturnsPageResult() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Pageable pageable = PageRequest.of(0, 10);
        List<Exam> exams = List.of(buildExamEntity("FA2026"));
        Page<Exam> examPage = new PageImpl<>(exams, pageable, 1);
        when(examRepository.findAllByDeletedAtIsNull(pageable)).thenReturn(examPage);

        // ── Act ───────────────────────────────────────────────────────────────
        Page<ExamResponse> result = examService.getAllExams(pageable);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("FA2026", result.getContent().get(0).getSemester());
    }

    // =========================================================================
    // searchExams()  — [N] 1 normal, [B] 1 boundary (blank params)
    // =========================================================================

    @Test
    @DisplayName("[N] searchExams - Tìm theo name + semester → trả về Page kết quả phù hợp")
    void searchExams_ValidFilters_ReturnsMatchingPage() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Pageable pageable = PageRequest.of(0, 10);
        List<Exam> exams = List.of(buildExamEntity("SP2026"));
        Page<Exam> examPage = new PageImpl<>(exams, pageable, 1);

        when(examRepository.searchExams(eq("Midterm"), eq("SP2026"), isNull(), eq(pageable)))
                .thenReturn(examPage);

        // ── Act ───────────────────────────────────────────────────────────────
        Page<ExamResponse> result = examService.searchExams("Midterm", "SP2026", null, pageable);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("[B] searchExams - Tất cả params blank/null → truyền null vào repository (no filter)")
    void searchExams_AllBlankParams_PassesNullToRepository() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Pageable pageable = PageRequest.of(0, 10);
        when(examRepository.searchExams(isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        // ── Act & Assert ──────────────────────────────────────────────────────
        // Blank string "  " phải bị normalize thành null
        assertDoesNotThrow(() -> examService.searchExams("  ", "  ", "  ", pageable));
        verify(examRepository).searchExams(isNull(), isNull(), isNull(), eq(pageable));
    }

    // =========================================================================
    // getExamById()  — [N] 1 normal, [A] 1 abnormal
    // =========================================================================

    @Test
    @DisplayName("[N] getExamById - Tìm thấy kỳ thi theo ID → trả về ExamResponse đầy đủ")
    void getExamById_ExistingId_ReturnsExamResponse() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Exam exam = buildExamEntity("SP2026");
        when(examRepository.findByExamIdAndDeletedAtIsNull(exam.getExamId()))
                .thenReturn(Optional.of(exam));

        // ── Act ───────────────────────────────────────────────────────────────
        ExamResponse response = examService.getExamById(exam.getExamId());

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals(exam.getExamId(), response.getExamId());
        assertEquals("SP2026", response.getSemester());
    }

    @Test
    @DisplayName("[A] getExamById - ID không tồn tại hoặc đã bị xóa → NotFoundException")
    void getExamById_NotExistingId_ThrowsNotFoundException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        UUID nonExistentId = UUID.randomUUID();
        when(examRepository.findByExamIdAndDeletedAtIsNull(nonExistentId)).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(NotFoundException.class, () -> examService.getExamById(nonExistentId));
    }

    // =========================================================================
    // updateExam()  — [N] 1 normal, [A] 2 abnormal
    // =========================================================================

    @Test
    @DisplayName("[N] updateExam - Đổi name và description (không đổi semester) → cập nhật thành công")
    void updateExam_NameAndDescription_UpdatesSuccessfully() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Exam exam = buildExamEntity("SP2026");
        UpdateExamRequest request = new UpdateExamRequest();
        request.setName("Updated Name");
        request.setDescription("Updated Desc");
        // semester và startTime/endTime giữ nguyên (null → không đổi)

        when(examRepository.findByExamIdAndDeletedAtIsNull(exam.getExamId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

        // ── Act ───────────────────────────────────────────────────────────────
        ExamResponse response = examService.updateExam(exam.getExamId(), request);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals("Updated Name", response.getName());
        verify(examRepository).save(any(Exam.class));
    }

    @Test
    @DisplayName("[A] updateExam - Exam ID không tồn tại → NotFoundException")
    void updateExam_NotFoundId_ThrowsNotFoundException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        UUID badId = UUID.randomUUID();
        UpdateExamRequest request = new UpdateExamRequest();
        request.setName("New Name");
        when(examRepository.findByExamIdAndDeletedAtIsNull(badId)).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(NotFoundException.class, () -> examService.updateExam(badId, request));
        verify(examRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] updateExam - Đổi sang semester đã thuộc kỳ thi khác → IllegalArgumentException")
    void updateExam_SemesterConflict_ThrowsIllegalArgumentException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Exam exam = buildExamEntity("SP2026");
        UpdateExamRequest request = new UpdateExamRequest();
        request.setSemester("FA2026"); // Đổi sang FA2026 đã tồn tại

        when(examRepository.findByExamIdAndDeletedAtIsNull(exam.getExamId())).thenReturn(Optional.of(exam));
        when(examRepository.existsBySemesterAndDeletedAtIsNullAndExamIdNot("FA2026", exam.getExamId()))
                .thenReturn(true);

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(IllegalArgumentException.class, () -> examService.updateExam(exam.getExamId(), request));
        verify(examRepository, never()).save(any());
    }

    // =========================================================================
    // deleteExam()  — [N] 1 normal (kỳ thi đã kết thúc), [A] 2 abnormal, [B] 1 boundary
    // =========================================================================

    @Test
    @DisplayName("[N] deleteExam - Kỳ thi đã kết thúc (endTime < now) → xóa thành công bất kể block")
    void deleteExam_ExamAlreadyEnded_SoftDeletesSuccessfully() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Exam exam = Exam.builder()
                .examId(UUID.randomUUID())
                .name("Old Exam")
                .semester("SP2020")
                .academicYear("2020")
                .startTime(OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, VN))
                .endTime(OffsetDateTime.of(2020, 4, 1, 0, 0, 0, 0, VN)) // đã kết thúc
                .gradingMode(GradingMode.MODE_1)
                .build();

        when(examRepository.findByExamIdAndDeletedAtIsNull(exam.getExamId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertDoesNotThrow(() -> examService.deleteExam(exam.getExamId()));
        assertNotNull(exam.getDeletedAt());
        verify(examRepository).save(exam);
        // blockRepository KHÔNG được gọi khi kỳ thi đã kết thúc
        verify(blockRepository, never()).existsBlockStartingOnOrBefore(any(), any());
    }

    @Test
    @DisplayName("[A] deleteExam - Exam ID không tồn tại → NotFoundException")
    void deleteExam_NotFoundId_ThrowsNotFoundException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        UUID badId = UUID.randomUUID();
        when(examRepository.findByExamIdAndDeletedAtIsNull(badId)).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(NotFoundException.class, () -> examService.deleteExam(badId));
        verify(examRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] deleteExam - Kỳ thi chưa kết thúc có block trong 14 ngày tới → ExamConflictException")
    void deleteExam_ActiveExamWithUpcomingBlock_ThrowsExamConflictException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        // endTime trong tương lai xa
        Exam exam = Exam.builder()
                .examId(UUID.randomUUID())
                .name("Active Exam")
                .semester("FA2026")
                .academicYear("2026")
                .startTime(OffsetDateTime.now().plusDays(1))
                .endTime(OffsetDateTime.now().plusDays(60))
                .gradingMode(GradingMode.MODE_1)
                .build();

        when(examRepository.findByExamIdAndDeletedAtIsNull(exam.getExamId())).thenReturn(Optional.of(exam));
        when(blockRepository.existsBlockStartingOnOrBefore(eq(exam.getExamId()), any()))
                .thenReturn(true); // có block trong 14 ngày

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(ExamConflictException.class, () -> examService.deleteExam(exam.getExamId()));
        verify(examRepository, never()).save(any());
    }

    @Test
    @DisplayName("[B] deleteExam - Kỳ thi chưa kết thúc nhưng KHÔNG có block trong 14 ngày → xóa thành công")
    void deleteExam_ActiveExamNoUpcomingBlock_SoftDeletesSuccessfully() {
        // ── Arrange ──────────────────────────────────────────────────────────
        Exam exam = Exam.builder()
                .examId(UUID.randomUUID())
                .name("Far Block Exam")
                .semester("SU2026")
                .academicYear("2026")
                .startTime(OffsetDateTime.now().plusDays(1))
                .endTime(OffsetDateTime.now().plusDays(60))
                .gradingMode(GradingMode.MODE_1)
                .build();

        when(examRepository.findByExamIdAndDeletedAtIsNull(exam.getExamId())).thenReturn(Optional.of(exam));
        when(blockRepository.existsBlockStartingOnOrBefore(eq(exam.getExamId()), any()))
                .thenReturn(false); // không có block trong 14 ngày
        when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertDoesNotThrow(() -> examService.deleteExam(exam.getExamId()));
        assertNotNull(exam.getDeletedAt());
        verify(examRepository).save(exam);
    }
}
