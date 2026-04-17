package agsfjope.backend.application.appealservices.impl;

import agsfjope.backend.application.dtos.requests.appeal.AssignAppealRequest;
import agsfjope.backend.application.dtos.requests.appeal.ConfirmAppealRequest;
import agsfjope.backend.application.dtos.responses.appeal.LecturerOptionResponse;
import agsfjope.backend.application.dtos.responses.appeal.StaffAppealDetailResponse;
import agsfjope.backend.application.dtos.responses.appeal.StaffAppealPageResponse;
import agsfjope.backend.application.notificationservices.NotificationService;
import agsfjope.backend.application.walletservices.WalletService;
import agsfjope.backend.configuration.storage.MinioConfig;
import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.enums.GradingResultStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.payment.PaymentRepository;
import agsfjope.backend.infrastructure.storage.MinioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests cho StaffAppealServiceImpl
 * Pattern: AAA (Arrange - Act - Assert)
 */
@ExtendWith(MockitoExtension.class)
class StaffAppealServiceImplTest {

    @Mock private AppealRepository appealRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private GradingResultRepository gradingResultRepository;
    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private MinioService minioService;
    @Mock private MinioConfig minioConfig;
    @Mock private WalletService walletService;
    @Mock private NotificationService notificationService;

    @Mock private agsfjope.backend.infrastructure.audit.AuditLogHelper auditLogHelper;
    @Mock private agsfjope.backend.core.repositories.submission.AnswerRepository answerRepository;

    @InjectMocks
    private StaffAppealServiceImpl staffAppealService;

    // =========================================================================
    // Helpers
    // =========================================================================

    private User buildLecturer(UUID id) {
        User u = new User();
        u.setUserId(id);
        u.setFullName("Nguyen Thi B - Lecturer");
        u.setEmail("lecturer@fpt.edu.vn");
        return u;
    }

    private User buildStudent(UUID id) {
        User u = new User();
        u.setUserId(id);
        u.setFullName("Nguyen Van A");
        u.setMssv("SE123456");
        u.setEmail("student@fpt.edu.vn");
        return u;
    }

    private Appeal buildFullAppeal(UUID appealId, AppealStatus status) {
        User student = buildStudent(UUID.randomUUID());

        Exam exam = new Exam(); exam.setName("PRO192"); exam.setSemester("FA2025");
        Block block = new Block(); block.setExam(exam); block.setName("Block 1");

        Submission submission = new Submission();
        submission.setSubmissionId(UUID.randomUUID());
        submission.setBlock(block);
        submission.setFilePath("submissions/test.zip");
        submission.setFileName("test.zip");

        Appeal a = new Appeal();
        a.setAppealId(appealId);
        a.setStudent(student);
        a.setSubmission(submission);
        a.setStatus(status);
        a.setReason("Tôi cần phúc khảo");
        a.setCreatedAt(OffsetDateTime.now());
        return a;
    }

    // =========================================================================
    // 1. getAppeals
    // =========================================================================

    @Test
    @DisplayName("[N] getAppeals - Truy vấn hợp lệ, có dữ liệu -> Trả về page response với overview")
    void getAppeals_ValidQuery_ReturnsPageResponse() {
        // Arrange
        Appeal a = buildFullAppeal(UUID.randomUUID(), AppealStatus.PENDING);
        Page<Appeal> appealPage = new PageImpl<>(List.of(a), PageRequest.of(0, 10), 1);

        when(appealRepository.count()).thenReturn(5L);
        when(appealRepository.countByStatus(anyString())).thenReturn(1L);
        when(appealRepository.searchAppealsForStaff(any(), any(), any(), any(), any()))
                .thenReturn(appealPage);
        when(gradingResultRepository.findBySubmission_SubmissionId(any())).thenReturn(Optional.empty());

        // Act
        StaffAppealPageResponse response = staffAppealService.getAppeals("PENDING", "", null, null, 0, 10);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getOverview());
        assertEquals(5L, response.getOverview().getTotal());
        assertEquals(1, response.getAppeals().size());
        assertEquals(0, response.getCurrentPage());
    }

    @Test
    @DisplayName("[N] getAppeals - Truy vấn với status null -> Tự động normalize thành null, vẫn chạy được")
    void getAppeals_NullStatus_NormalizesParam() {
        // Arrange
        Page<Appeal> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(appealRepository.count()).thenReturn(0L);
        when(appealRepository.countByStatus(anyString())).thenReturn(0L);
        when(appealRepository.searchAppealsForStaff(isNull(), any(), any(), any(), any()))
                .thenReturn(emptyPage);

        // Act
        StaffAppealPageResponse response = staffAppealService.getAppeals(null, null, null, null, 0, 10);

        // Assert
        assertNotNull(response);
        assertEquals(0, response.getAppeals().size());
        verify(appealRepository).searchAppealsForStaff(isNull(), eq(""), isNull(), isNull(), any());
    }

    // =========================================================================
    // 2. getAppealDetail
    // =========================================================================

    @Test
    @DisplayName("[N] getAppealDetail - appealId tồn tại -> Trả về chi tiết đầy đủ")
    void getAppealDetail_ExistingId_ReturnsDetail() {
        // Arrange
        UUID appealId = UUID.randomUUID();
        Appeal appeal = buildFullAppeal(appealId, AppealStatus.PENDING);

        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));
        when(gradingResultRepository.findBySubmission_SubmissionId(any())).thenReturn(Optional.empty());
        when(paymentRepository.findByAppealId(appealId)).thenReturn(Optional.empty());

        // Act
        StaffAppealDetailResponse response = staffAppealService.getAppealDetail(appealId);

        // Assert
        assertNotNull(response);
        assertEquals(appealId, response.getAppealId());
        assertEquals(AppealStatus.PENDING, response.getStatus());
    }

    @Test
    @DisplayName("[A] getAppealDetail - appealId không tồn tại -> Throw IllegalArgumentException")
    void getAppealDetail_NotFound_ThrowsException() {
        UUID appealId = UUID.randomUUID();
        when(appealRepository.findById(appealId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> staffAppealService.getAppealDetail(appealId));
    }

    // =========================================================================
    // 3. assignLecturer
    // =========================================================================

    @Test
    @DisplayName("[N] assignLecturer - Appeal PENDING, deadline null -> Tự động set deadline theo config")
    void assignLecturer_PendingAppeal_NullDeadline_SetsAutoDeadline() {
        // Arrange
        UUID appealId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        Appeal appeal = buildFullAppeal(appealId, AppealStatus.PENDING);
        User lecturer = buildLecturer(lecturerId);
        User staff = new User(); staff.setUserId(staffId);

        AssignAppealRequest request = new AssignAppealRequest();
        request.setLecturerId(lecturerId);
        request.setDeadlineAt(null); // auto deadline

        // Use default = 7 days
        when(systemConfigRepository.findByConfigKey("APPEAL_DEADLINE_DAYS")).thenReturn(Optional.empty());
        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));
        when(userRepository.findById(lecturerId)).thenReturn(Optional.of(lecturer));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(appealRepository.save(appeal)).thenReturn(appeal);
        when(gradingResultRepository.findBySubmission_SubmissionId(any())).thenReturn(Optional.empty());
        when(paymentRepository.findByAppealId(appealId)).thenReturn(Optional.empty());

        // Act
        StaffAppealDetailResponse response = staffAppealService.assignLecturer(appealId, request, staffId);

        // Assert
        assertNotNull(response);
        assertEquals(AppealStatus.PROCESSING, appeal.getStatus());
        assertEquals(lecturer, appeal.getAssignedLecturer());
        assertNotNull(appeal.getDeadlineAt());
        verify(appealRepository).save(appeal);
    }

    @Test
    @DisplayName("[N] assignLecturer - Appeal PENDING, deadline tương lai -> Gán deadline tùy chỉnh")
    void assignLecturer_PendingAppeal_FutureDeadline_UsesSuppliedDeadline() {
        // Arrange
        UUID appealId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        Appeal appeal = buildFullAppeal(appealId, AppealStatus.PENDING);
        User lecturer = buildLecturer(lecturerId);
        User staff = new User(); staff.setUserId(staffId);

        OffsetDateTime futureDeadline = OffsetDateTime.now().plusDays(5);
        AssignAppealRequest request = new AssignAppealRequest();
        request.setLecturerId(lecturerId);
        request.setDeadlineAt(futureDeadline);

        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));
        when(userRepository.findById(lecturerId)).thenReturn(Optional.of(lecturer));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(appealRepository.save(appeal)).thenReturn(appeal);
        when(gradingResultRepository.findBySubmission_SubmissionId(any())).thenReturn(Optional.empty());
        when(paymentRepository.findByAppealId(appealId)).thenReturn(Optional.empty());

        // Act
        staffAppealService.assignLecturer(appealId, request, staffId);

        // Assert
        assertEquals(futureDeadline, appeal.getDeadlineAt());
        assertEquals(AppealStatus.PROCESSING, appeal.getStatus());
    }

    @Test
    @DisplayName("[A] assignLecturer - Appeal không PENDING (đang PROCESSING) -> Throw IllegalStateException")
    void assignLecturer_NotPendingStatus_ThrowsException() {
        UUID appealId = UUID.randomUUID();
        Appeal appeal = buildFullAppeal(appealId, AppealStatus.PROCESSING);

        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));

        AssignAppealRequest request = new AssignAppealRequest();
        request.setLecturerId(UUID.randomUUID());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> staffAppealService.assignLecturer(appealId, request, UUID.randomUUID()));
        assertTrue(ex.getMessage().contains("Chỉ có thể phân công"));
    }

    @Test
    @DisplayName("[B] assignLecturer - Deadline là thời điểm đã qua -> Throw IllegalArgumentException")
    void assignLecturer_PastDeadline_ThrowsException() {
        UUID appealId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        Appeal appeal = buildFullAppeal(appealId, AppealStatus.PENDING);
        User lecturer = buildLecturer(lecturerId);

        OffsetDateTime pastDate = OffsetDateTime.now().minusDays(1);
        AssignAppealRequest request = new AssignAppealRequest();
        request.setLecturerId(lecturerId);
        request.setDeadlineAt(pastDate);

        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));
        when(userRepository.findById(lecturerId)).thenReturn(Optional.of(lecturer));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(new User()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> staffAppealService.assignLecturer(appealId, request, staffId));
        assertTrue(ex.getMessage().contains("Deadline không được chọn trong quá khứ"));
    }

    // =========================================================================
    // 4. getLecturerOptions
    // =========================================================================

    @Test
    @DisplayName("[N] getLecturerOptions - Có 1 giảng viên -> Trả về danh sách 1 item")
    void getLecturerOptions_HasLecturers_ReturnsList() {
        // Arrange
        User lecturer = buildLecturer(UUID.randomUUID());
        when(userRepository.findByRole_NameAndDeletedAtIsNull("LECTURER")).thenReturn(List.of(lecturer));
        when(appealRepository.countActiveAppealsByLecturer(lecturer.getUserId())).thenReturn(2L);

        // Act
        List<LecturerOptionResponse> result = staffAppealService.getLecturerOptions();

        // Assert
        assertEquals(1, result.size());
        assertEquals(lecturer.getUserId(), result.get(0).getLecturerId());
        assertEquals(2L, result.get(0).getActiveAppealCount());
    }

    @Test
    @DisplayName("[N] getLecturerOptions - Không có giảng viên nào -> Trả về mảng rỗng")
    void getLecturerOptions_NoLecturers_ReturnsEmptyList() {
        when(userRepository.findByRole_NameAndDeletedAtIsNull("LECTURER")).thenReturn(Collections.emptyList());
        List<LecturerOptionResponse> result = staffAppealService.getLecturerOptions();
        assertTrue(result.isEmpty());
    }

    // =========================================================================
    // 5. confirmAppeal
    // =========================================================================

    @Test
    @DisplayName("[N] confirmAppeal - Approve, newScore 8.0 (PASS) -> Cập nhật điểm, hoàn tiền ví")
    void confirmAppeal_Approve_UpdatesScoreAndRefunds() {
        // Arrange
        UUID appealId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        User student = buildStudent(studentId);
        Appeal appeal = buildFullAppeal(appealId, AppealStatus.COMPLETED);
        appeal.setStudent(student);
        appeal.setNewScore(new BigDecimal("8.0"));

        GradingResult gr = new GradingResult();
        gr.setTotalScore(new BigDecimal("5.0"));

        ConfirmAppealRequest request = new ConfirmAppealRequest();
        request.setIsApprove(true);

        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));
        when(gradingResultRepository
                .findBySubmission_SubmissionId(appeal.getSubmission().getSubmissionId()))
                .thenReturn(Optional.of(gr));
        when(systemConfigRepository.findByConfigKey("APPEAL_FEE")).thenReturn(Optional.empty()); // default 200k
        when(appealRepository.save(appeal)).thenReturn(appeal);
        when(paymentRepository.findByAppealId(appealId)).thenReturn(Optional.empty());

        // Act
        StaffAppealDetailResponse response = staffAppealService.confirmAppeal(appealId, request, staffId);

        // Assert
        assertNotNull(response);
        assertEquals(AppealStatus.APPROVED, appeal.getStatus());
        assertEquals(new BigDecimal("8.0"), gr.getTotalScore());
        assertEquals(GradingResultStatus.PASS, gr.getStatus());
        verify(gradingResultRepository).save(gr);
        verify(walletService).refundToWallet(eq(studentId), eq(new BigDecimal("200000")), eq(appealId));
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("[N] confirmAppeal - Deny -> Chuyển DENIED, gửi notification")
    void confirmAppeal_Deny_SendsNotification() {
        // Arrange
        UUID appealId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        User student = buildStudent(studentId);
        Appeal appeal = buildFullAppeal(appealId, AppealStatus.COMPLETED);
        appeal.setStudent(student);

        ConfirmAppealRequest request = new ConfirmAppealRequest();
        request.setIsApprove(false);

        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));
        when(appealRepository.save(appeal)).thenReturn(appeal);
        when(paymentRepository.findByAppealId(appealId)).thenReturn(Optional.empty());
        when(gradingResultRepository.findBySubmission_SubmissionId(any())).thenReturn(Optional.empty());

        // Act
        staffAppealService.confirmAppeal(appealId, request, UUID.randomUUID());

        // Assert
        assertEquals(AppealStatus.DENIED, appeal.getStatus());
        verify(walletService, never()).refundToWallet(any(), any(), any());
        verify(notificationService).createNotification(eq(studentId), contains("Phúc khảo"), any(), eq("APPEAL"), eq(appealId));
    }

    @Test
    @DisplayName("[A] confirmAppeal - Appeal không COMPLETED (đang PENDING) -> Throw IllegalStateException")
    void confirmAppeal_NotCompleted_ThrowsException() {
        UUID appealId = UUID.randomUUID();
        Appeal appeal = buildFullAppeal(appealId, AppealStatus.PENDING);

        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> staffAppealService.confirmAppeal(appealId, new ConfirmAppealRequest(), UUID.randomUUID()));
        assertTrue(ex.getMessage().contains("COMPLETED"));
    }

    @Test
    @DisplayName("[B] confirmAppeal - Approve, newScore 2.0 (FAIL) -> Cập nhật điểm FAIL, vẫn hoàn tiền")
    void confirmAppeal_Approve_LowScore_SetsGradingFail() {
        // Arrange
        UUID appealId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        User student = buildStudent(studentId);
        Appeal appeal = buildFullAppeal(appealId, AppealStatus.COMPLETED);
        appeal.setStudent(student);
        appeal.setNewScore(new BigDecimal("2.0")); // below 4.0 → FAIL

        GradingResult gr = new GradingResult();
        gr.setTotalScore(new BigDecimal("3.0"));

        ConfirmAppealRequest request = new ConfirmAppealRequest();
        request.setIsApprove(true);

        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));
        when(gradingResultRepository
                .findBySubmission_SubmissionId(appeal.getSubmission().getSubmissionId()))
                .thenReturn(Optional.of(gr));
        when(systemConfigRepository.findByConfigKey("APPEAL_FEE")).thenReturn(Optional.empty());
        when(appealRepository.save(appeal)).thenReturn(appeal);
        when(paymentRepository.findByAppealId(appealId)).thenReturn(Optional.empty());

        // Act
        staffAppealService.confirmAppeal(appealId, request, UUID.randomUUID());

        // Assert
        assertEquals(new BigDecimal("2.0"), gr.getTotalScore());
        assertEquals(GradingResultStatus.FAIL, gr.getStatus()); // < 4.0 -> FAIL
        verify(walletService).refundToWallet(eq(studentId), any(), eq(appealId));
    }

    // =========================================================================
    // 6. downloadSubmission
    // =========================================================================

    @Test
    @DisplayName("[N] downloadSubmission - Tồn tại file -> Trả về InputStream hợp lệ")
    void downloadSubmission_FileExists_ReturnsStream() {
        // Arrange
        UUID appealId = UUID.randomUUID();
        Appeal appeal = buildFullAppeal(appealId, AppealStatus.PROCESSING);

        MinioConfig.BucketConfig buckets = mock(MinioConfig.BucketConfig.class);
        when(minioConfig.getBucket()).thenReturn(buckets);
        when(buckets.getSubmissions()).thenReturn("submissions-bucket");

        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));
        when(minioService.downloadFile("submissions-bucket", "submissions/test.zip"))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        // Act
        var stream = staffAppealService.downloadSubmission(appealId);

        // Assert
        assertNotNull(stream);
        verify(minioService).downloadFile("submissions-bucket", "submissions/test.zip");
    }

    @Test
    @DisplayName("[A] downloadSubmission - File path là null -> Throw IllegalStateException")
    void downloadSubmission_NullFilePath_ThrowsException() {
        // Arrange
        UUID appealId = UUID.randomUUID();
        Appeal appeal = buildFullAppeal(appealId, AppealStatus.PROCESSING);
        appeal.getSubmission().setFilePath(null); // no file

        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));

        // Act & Assert
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> staffAppealService.downloadSubmission(appealId));
        assertTrue(ex.getMessage().contains("Không tìm thấy file"));
    }
}
