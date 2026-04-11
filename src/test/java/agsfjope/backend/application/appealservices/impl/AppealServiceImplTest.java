package agsfjope.backend.application.appealservices.impl;

import agsfjope.backend.application.dtos.requests.appeal.CreateAppealRequest;
import agsfjope.backend.application.dtos.responses.appeal.CreateAppealResponse;
import agsfjope.backend.application.dtos.responses.appeal.MyAppealsPageResponse;
import agsfjope.backend.application.walletservices.WalletService;
import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.enums.SubmissionStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

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
 * Unit Tests cho AppealServiceImpl
 * Pattern: AAA (Arrange - Act - Assert)
 */
@ExtendWith(MockitoExtension.class)
class AppealServiceImplTest {

    @Mock private AppealRepository appealRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private GradingResultRepository gradingResultRepository;
    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private WalletService walletService;

    @InjectMocks
    private AppealServiceImpl appealService;

    // =========================================================================
    // Helper builders
    // =========================================================================
    private User buildStudent(UUID id) {
        User u = new User();
        u.setUserId(id);
        u.setFullName("Nguyen Van A");
        return u;
    }

    private Submission buildSubmission(UUID submissionId, UUID studentId, SubmissionStatus status) {
        User student = buildStudent(studentId);

        Exam exam = new Exam();
        exam.setName("PRO192");
        exam.setSemester("FA2025");

        Block block = new Block();
        block.setExam(exam);

        Submission s = new Submission();
        s.setSubmissionId(submissionId);
        s.setStudent(student);
        s.setStatus(status);
        s.setBlock(block);
        return s;
    }

    private Appeal buildAppeal(UUID appealId, UUID studentId, AppealStatus status) {
        Appeal a = new Appeal();
        a.setAppealId(appealId);
        a.setStatus(status);
        a.setStudent(buildStudent(studentId));
        a.setCreatedAt(OffsetDateTime.now());

        Exam exam = new Exam(); exam.setName("PRO192"); exam.setSemester("FA2025");
        Block block = new Block(); block.setExam(exam);
        Submission s = new Submission(); s.setSubmissionId(UUID.randomUUID()); s.setBlock(block);
        a.setSubmission(s);
        return a;
    }

    // =========================================================================
    // 1. createAppeal
    // =========================================================================

    @Test
    @DisplayName("[N] createAppeal - Hợp lệ, ví đủ tiền -> Tạo appeal PENDING thành công")
    void createAppeal_ValidRequest_SuccessfullyCreatesAppeal() {
        // Arrange
        UUID studentId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID appealId = UUID.randomUUID();

        CreateAppealRequest request = new CreateAppealRequest();
        request.setSubmissionId(submissionId);
        request.setReason("Tôi muốn phúc khảo");

        User student = buildStudent(studentId);
        Submission submission = buildSubmission(submissionId, studentId, SubmissionStatus.GRADED);

        Appeal savedAppeal = new Appeal();
        savedAppeal.setAppealId(appealId);

        Wallet updatedWallet = new Wallet();
        updatedWallet.setBalance(new BigDecimal("800000"));

        GradingResult gr = new GradingResult();
        gr.setTotalScore(new BigDecimal("7.5"));

        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(appealRepository.existsBySubmission_SubmissionId(submissionId)).thenReturn(false);
        when(systemConfigRepository.findByConfigKey("APPEAL_FEE")).thenReturn(Optional.empty()); // use default 200k
        when(gradingResultRepository.findBySubmission_SubmissionId(submissionId)).thenReturn(Optional.of(gr));
        when(appealRepository.save(any(Appeal.class))).thenReturn(savedAppeal);
        when(walletService.debitWalletForAppeal(eq(studentId), any(), eq(appealId))).thenReturn(updatedWallet);

        // Act
        CreateAppealResponse response = appealService.createAppeal(studentId, request);

        // Assert
        assertNotNull(response);
        assertEquals(appealId, response.getAppealId());
        assertEquals(new BigDecimal("200000"), response.getAmount()); // default fee
        assertEquals(new BigDecimal("800000"), response.getWalletBalanceAfter());
        assertEquals(new BigDecimal("7.5"), response.getOriginalScore());
        verify(appealRepository).updateStatus(appealId, AppealStatus.PENDING);
        verify(walletService).debitWalletForAppeal(eq(studentId), eq(new BigDecimal("200000")), eq(appealId));
    }

    @Test
    @DisplayName("[N] createAppeal - Config có APPEAL_FEE = 150000 -> Dùng phí từ config")
    void createAppeal_ConfigFeeExists_UsesConfigFee() {
        // Arrange
        UUID studentId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID appealId = UUID.randomUUID();

        CreateAppealRequest request = new CreateAppealRequest();
        request.setSubmissionId(submissionId);
        request.setReason("test");

        User student = buildStudent(studentId);
        Submission submission = buildSubmission(submissionId, studentId, SubmissionStatus.GRADED);
        Appeal savedAppeal = new Appeal(); savedAppeal.setAppealId(appealId);

        SystemConfig feeConfig = new SystemConfig();
        feeConfig.setConfigValue("150000");
        Wallet wallet = new Wallet(); wallet.setBalance(new BigDecimal("500000"));

        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(appealRepository.existsBySubmission_SubmissionId(submissionId)).thenReturn(false);
        when(systemConfigRepository.findByConfigKey("APPEAL_FEE")).thenReturn(Optional.of(feeConfig));
        when(gradingResultRepository.findBySubmission_SubmissionId(submissionId)).thenReturn(Optional.empty());
        when(appealRepository.save(any(Appeal.class))).thenReturn(savedAppeal);
        when(walletService.debitWalletForAppeal(eq(studentId), eq(new BigDecimal("150000")), eq(appealId))).thenReturn(wallet);

        // Act
        CreateAppealResponse response = appealService.createAppeal(studentId, request);

        // Assert
        assertEquals(new BigDecimal("150000"), response.getAmount());
        assertEquals(BigDecimal.ZERO, response.getOriginalScore()); // no grading result
    }

    @Test
    @DisplayName("[A] createAppeal - Sinh viên không tồn tại -> Throw RuntimeException")
    void createAppeal_StudentNotFound_ThrowsException() {
        UUID studentId = UUID.randomUUID();
        when(userRepository.findById(studentId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
                appealService.createAppeal(studentId, new CreateAppealRequest()));
    }

    @Test
    @DisplayName("[A] createAppeal - Submission không tồn tại -> Throw IllegalArgumentException")
    void createAppeal_SubmissionNotFound_ThrowsException() {
        UUID studentId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();

        CreateAppealRequest req = new CreateAppealRequest();
        req.setSubmissionId(submissionId);

        when(userRepository.findById(studentId)).thenReturn(Optional.of(buildStudent(studentId)));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> appealService.createAppeal(studentId, req));
    }

    @Test
    @DisplayName("[A] createAppeal - Submission không thuộc sinh viên này -> Throw AccessDeniedException")
    void createAppeal_SubmissionBelongsToAnotherStudent_ThrowsAccessDenied() {
        UUID studentId = UUID.randomUUID();
        UUID otherStudentId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();

        CreateAppealRequest req = new CreateAppealRequest();
        req.setSubmissionId(submissionId);

        Submission submission = buildSubmission(submissionId, otherStudentId, SubmissionStatus.GRADED);

        when(userRepository.findById(studentId)).thenReturn(Optional.of(buildStudent(studentId)));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        assertThrows(AccessDeniedException.class, () -> appealService.createAppeal(studentId, req));
    }

    @Test
    @DisplayName("[B] createAppeal - Submission chưa được chấm điểm (SUBMITTED) -> Throw IllegalStateException")
    void createAppeal_SubmissionNotGraded_ThrowsException() {
        UUID studentId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();

        CreateAppealRequest req = new CreateAppealRequest();
        req.setSubmissionId(submissionId);

        Submission submission = buildSubmission(submissionId, studentId, SubmissionStatus.SUBMITTED);

        when(userRepository.findById(studentId)).thenReturn(Optional.of(buildStudent(studentId)));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> appealService.createAppeal(studentId, req));
        assertTrue(ex.getMessage().contains("chưa được chấm điểm"));
    }

    @Test
    @DisplayName("[B] createAppeal - Submission đã có Appeal tồn tại (BR-01) -> Throw IllegalStateException")
    void createAppeal_DuplicateAppeal_ThrowsException() {
        UUID studentId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();

        CreateAppealRequest req = new CreateAppealRequest();
        req.setSubmissionId(submissionId);

        Submission submission = buildSubmission(submissionId, studentId, SubmissionStatus.GRADED);

        when(userRepository.findById(studentId)).thenReturn(Optional.of(buildStudent(studentId)));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(appealRepository.existsBySubmission_SubmissionId(submissionId)).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> appealService.createAppeal(studentId, req));
        assertTrue(ex.getMessage().contains("Đã tồn tại đơn phúc khảo"));
    }

    @Test
    @DisplayName("[B] createAppeal - Số dư ví không đủ -> WalletService throw, rollback transaction")
    void createAppeal_InsufficientWalletBalance_ThrowsException() {
        UUID studentId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID appealId = UUID.randomUUID();

        CreateAppealRequest req = new CreateAppealRequest();
        req.setSubmissionId(submissionId);
        req.setReason("test");

        Submission submission = buildSubmission(submissionId, studentId, SubmissionStatus.GRADED);
        Appeal savedAppeal = new Appeal(); savedAppeal.setAppealId(appealId);

        when(userRepository.findById(studentId)).thenReturn(Optional.of(buildStudent(studentId)));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(appealRepository.existsBySubmission_SubmissionId(submissionId)).thenReturn(false);
        when(systemConfigRepository.findByConfigKey("APPEAL_FEE")).thenReturn(Optional.empty());
        when(gradingResultRepository.findBySubmission_SubmissionId(submissionId)).thenReturn(Optional.empty());
        when(appealRepository.save(any(Appeal.class))).thenReturn(savedAppeal);
        when(walletService.debitWalletForAppeal(any(), any(), any()))
                .thenThrow(new IllegalStateException("Số dư ví không đủ"));

        assertThrows(IllegalStateException.class, () -> appealService.createAppeal(studentId, req));
        // updateStatus KHÔNG được gọi vì wallet debit đã throw
        verify(appealRepository, never()).updateStatus(any(), any());
    }

    // =========================================================================
    // 2. getMyAppeals
    // =========================================================================

    @Test
    @DisplayName("[N] getMyAppeals - Có 2 appeal -> Trả về response với đầy đủ stats")
    void getMyAppeals_HasAppeals_ReturnsPageResponse() {
        UUID studentId = UUID.randomUUID();
        UUID appealId = UUID.randomUUID();

        Appeal a = buildAppeal(appealId, studentId, AppealStatus.PENDING);

        when(appealRepository.countByStudentAndStatus(studentId, "PENDING")).thenReturn(1L);
        when(appealRepository.countByStudentAndStatus(studentId, "PROCESSING")).thenReturn(0L);
        when(appealRepository.countByStudentAndStatus(studentId, "COMPLETED")).thenReturn(1L);
        when(appealRepository.countByStudentAndStatus(studentId, "APPROVED")).thenReturn(1L);
        when(appealRepository.countByStudentAndStatus(studentId, "DENIED")).thenReturn(0L);
        when(appealRepository.findByStudentOrderByCreatedAtDesc(studentId)).thenReturn(List.of(a));
        when(gradingResultRepository.findBySubmission_SubmissionId(a.getSubmission().getSubmissionId()))
                .thenReturn(Optional.empty());

        // Act
        MyAppealsPageResponse response = appealService.getMyAppeals(studentId);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getTotalAppeals());
        assertEquals(2, response.getProcessingCount()); // pending + processing + completed
        assertEquals(1, response.getApprovedCount());
        assertEquals(1, response.getAppeals().size());
        assertEquals(a.getSubmission().getSubmissionId(), response.getAppeals().get(0).getSubmissionId());
    }

    @Test
    @DisplayName("[N] getMyAppeals - Không có appeal nào -> Trả về danh sách rỗng")
    void getMyAppeals_NoAppeals_ReturnsEmptyList() {
        UUID studentId = UUID.randomUUID();

        when(appealRepository.countByStudentAndStatus(any(), anyString())).thenReturn(0L);
        when(appealRepository.findByStudentOrderByCreatedAtDesc(studentId)).thenReturn(Collections.emptyList());

        MyAppealsPageResponse response = appealService.getMyAppeals(studentId);

        assertNotNull(response);
        assertEquals(0, response.getTotalAppeals());
        assertTrue(response.getAppeals().isEmpty());
    }
}
