package agsfjope.backend.application.appealservices.impl;

import agsfjope.backend.application.dtos.requests.appeal.ReviewAppealRequest;
import agsfjope.backend.application.dtos.responses.appeal.LecturerAppealDetailResponse;
import agsfjope.backend.application.dtos.responses.appeal.LecturerAppealPageResponse;
import agsfjope.backend.configuration.storage.MinioConfig;
import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests cho LecturerAppealServiceImpl
 * Pattern: AAA (Arrange - Act - Assert)
 */
@ExtendWith(MockitoExtension.class)
class LecturerAppealServiceImplTest {

    @Mock private AppealRepository appealRepository;
    @Mock private GradingResultRepository gradingResultRepository;
    @Mock private MinioService minioService;
    @Mock private MinioConfig minioConfig;

    @Mock private agsfjope.backend.infrastructure.audit.AuditLogHelper auditLogHelper;
    @Mock private agsfjope.backend.core.repositories.submission.AnswerRepository answerRepository;

    @InjectMocks
    private LecturerAppealServiceImpl lecturerAppealService;

    // =========================================================================
    // Helpers
    // =========================================================================

    private User buildLecturer(UUID id) {
        User u = new User();
        u.setUserId(id);
        u.setFullName("Nguyen Thi B");
        u.setMssv("GV001");
        return u;
    }

    private User buildStudent(UUID id) {
        User u = new User();
        u.setUserId(id);
        u.setFullName("Nguyen Van A");
        u.setMssv("SE123456");
        return u;
    }

    private Appeal buildAssignedAppeal(UUID appealId, UUID lecturerId, AppealStatus status) {
        User lecturer = buildLecturer(lecturerId);
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
        a.setAssignedLecturer(lecturer);
        a.setSubmission(submission);
        a.setStatus(status);
        a.setReason("Phúc khảo");
        a.setCreatedAt(OffsetDateTime.now());
        a.setDeadlineAt(OffsetDateTime.now().plusDays(3));
        return a;
    }

    // =========================================================================
    // 1. getAppeals
    // =========================================================================

    @Test
    @DisplayName("[N] getAppeals - Lecturer có 1 appeal PROCESSING được phân công -> Trả về page response")
    void getAppeals_ValidLecturerId_ReturnsPageResponse() {
        // Arrange
        UUID lecturerId = UUID.randomUUID();
        Appeal a = buildAssignedAppeal(UUID.randomUUID(), lecturerId, AppealStatus.PROCESSING);
        Page<Appeal> page = new PageImpl<>(List.of(a), PageRequest.of(0, 10), 1);

        when(appealRepository.countByAssignedLecturerAndStatus(any(), anyString())).thenReturn(1L);
        when(appealRepository.countOverdueByAssignedLecturer(eq(lecturerId), any())).thenReturn(0L);
        when(appealRepository.searchAppealsForLecturer(eq(lecturerId), any(), any(), any())).thenReturn(page);
        when(gradingResultRepository.findBySubmission_SubmissionId(any())).thenReturn(Optional.empty());

        // Act
        LecturerAppealPageResponse response = lecturerAppealService.getAppeals(lecturerId, "PROCESSING", "", 0, 10);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getOverview());
        assertEquals(1, response.getAppeals().size());
        assertEquals(0, response.getCurrentPage());
    }

    @Test
    @DisplayName("[N] getAppeals - Không có appeal nào được phân công -> Trả về danh sách rỗng")
    void getAppeals_NoAppeals_ReturnsEmptyList() {
        // Arrange
        UUID lecturerId = UUID.randomUUID();
        Page<Appeal> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);

        when(appealRepository.countByAssignedLecturerAndStatus(any(), anyString())).thenReturn(0L);
        when(appealRepository.countOverdueByAssignedLecturer(eq(lecturerId), any())).thenReturn(0L);
        when(appealRepository.searchAppealsForLecturer(eq(lecturerId), isNull(), eq(""), any())).thenReturn(emptyPage);

        // Act
        LecturerAppealPageResponse response = lecturerAppealService.getAppeals(lecturerId, null, null, 0, 10);

        // Assert
        assertNotNull(response);
        assertTrue(response.getAppeals().isEmpty());
        assertEquals(0L, response.getOverview().getTotalAssigned());
    }

    // =========================================================================
    // 2. getAppealDetail
    // =========================================================================

    @Test
    @DisplayName("[N] getAppealDetail - appealId tồn tại, lecturer được phân công -> Trả về chi tiết")
    void getAppealDetail_ValidOwnership_ReturnsDetail() {
        // Arrange
        UUID lecturerId = UUID.randomUUID();
        UUID appealId = UUID.randomUUID();
        Appeal appeal = buildAssignedAppeal(appealId, lecturerId, AppealStatus.PROCESSING);

        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));
        when(gradingResultRepository.findBySubmission_SubmissionId(any())).thenReturn(Optional.empty());

        // Act
        LecturerAppealDetailResponse response = lecturerAppealService.getAppealDetail(lecturerId, appealId);

        // Assert
        assertNotNull(response);
        assertEquals(appealId, response.getAppealId());
        assertEquals(AppealStatus.PROCESSING, response.getStatus());
    }

    @Test
    @DisplayName("[A] getAppealDetail - appealId không tồn tại -> Throw IllegalArgumentException")
    void getAppealDetail_AppealNotFound_ThrowsException() {
        UUID lecturerId = UUID.randomUUID();
        UUID appealId = UUID.randomUUID();
        when(appealRepository.findById(appealId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> lecturerAppealService.getAppealDetail(lecturerId, appealId));
    }

    @Test
    @DisplayName("[A] getAppealDetail - Appeal được phân công cho giảng viên khác -> Throw IllegalStateException")
    void getAppealDetail_WrongLecturer_ThrowsException() {
        // Arrange
        UUID actualLecturerId = UUID.randomUUID();
        UUID requestingLecturerId = UUID.randomUUID(); // giảng viên khác
        UUID appealId = UUID.randomUUID();

        Appeal appeal = buildAssignedAppeal(appealId, actualLecturerId, AppealStatus.PROCESSING);

        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));

        // Act & Assert
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> lecturerAppealService.getAppealDetail(requestingLecturerId, appealId));
        assertTrue(ex.getMessage().contains("không có quyền"));
    }

    // =========================================================================
    // 3. submitReview
    // =========================================================================

    @Test
    @DisplayName("[N] submitReview - Appeal PROCESSING, newScore hợp lệ -> Cập nhật COMPLETED")
    void submitReview_ValidProcessingAppeal_SetCompleted() {
        // Arrange
        UUID lecturerId = UUID.randomUUID();
        UUID appealId = UUID.randomUUID();
        Appeal appeal = buildAssignedAppeal(appealId, lecturerId, AppealStatus.PROCESSING);

        ReviewAppealRequest request = new ReviewAppealRequest();
        request.setNewScore(new BigDecimal("8.5"));
        request.setLecturerComment("Bài làm tốt, tăng điểm");
        request.setNewQuestionScores(Map.of("Q1", new BigDecimal("8.5")));

        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));
        when(appealRepository.save(appeal)).thenReturn(appeal);
        when(gradingResultRepository.findBySubmission_SubmissionId(any())).thenReturn(Optional.empty());

        // Act
        LecturerAppealDetailResponse response = lecturerAppealService.submitReview(lecturerId, appealId, request);

        // Assert
        assertNotNull(response);
        assertEquals(AppealStatus.COMPLETED, appeal.getStatus());
        assertEquals(new BigDecimal("8.5"), appeal.getNewScore());
        assertEquals("Bài làm tốt, tăng điểm", appeal.getLecturerComment());
        verify(appealRepository).save(appeal);
    }

    @Test
    @DisplayName("[N] submitReview - Điểm review = 0.0 (valid boundary) -> Lưu thành công COMPLETED")
    void submitReview_ZeroScore_SavesSuccessfully() {
        UUID lecturerId = UUID.randomUUID();
        UUID appealId = UUID.randomUUID();
        Appeal appeal = buildAssignedAppeal(appealId, lecturerId, AppealStatus.PROCESSING);

        ReviewAppealRequest request = new ReviewAppealRequest();
        request.setNewScore(BigDecimal.ZERO);
        request.setLecturerComment("Không đạt yêu cầu");

        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));
        when(appealRepository.save(appeal)).thenReturn(appeal);
        when(gradingResultRepository.findBySubmission_SubmissionId(any())).thenReturn(Optional.empty());

        // Act
        lecturerAppealService.submitReview(lecturerId, appealId, request);

        // Assert
        assertEquals(BigDecimal.ZERO, appeal.getNewScore());
        assertEquals(AppealStatus.COMPLETED, appeal.getStatus());
    }

    @Test
    @DisplayName("[A] submitReview - Appeal không PROCESSING (đang COMPLETED) -> Throw IllegalStateException")
    void submitReview_NotProcessingStatus_ThrowsException() {
        // Arrange
        UUID lecturerId = UUID.randomUUID();
        UUID appealId = UUID.randomUUID();
        Appeal appeal = buildAssignedAppeal(appealId, lecturerId, AppealStatus.COMPLETED);

        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));

        // Act & Assert
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> lecturerAppealService.submitReview(lecturerId, appealId, new ReviewAppealRequest()));
        assertTrue(ex.getMessage().contains("PROCESSING"));
    }

    @Test
    @DisplayName("[B] submitReview - Giảng viên khác cố nộp review -> Throw IllegalStateException (ownership check)")
    void submitReview_WrongLecturer_ThrowsOwnershipException() {
        // Arrange
        UUID actualLecturerId = UUID.randomUUID();
        UUID wrongLecturerId = UUID.randomUUID();
        UUID appealId = UUID.randomUUID();
        Appeal appeal = buildAssignedAppeal(appealId, actualLecturerId, AppealStatus.PROCESSING);

        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));

        // Act & Assert
        assertThrows(IllegalStateException.class,
                () -> lecturerAppealService.submitReview(wrongLecturerId, appealId, new ReviewAppealRequest()));
        verify(appealRepository, never()).save(any());
    }

    // =========================================================================
    // 4. downloadSubmission
    // =========================================================================

    @Test
    @DisplayName("[N] downloadSubmission - File có sẵn trong MinIO -> Trả về InputStream")
    void downloadSubmission_FileExists_ReturnsStream() {
        // Arrange
        UUID lecturerId = UUID.randomUUID();
        UUID appealId = UUID.randomUUID();
        Appeal appeal = buildAssignedAppeal(appealId, lecturerId, AppealStatus.PROCESSING);

        MinioConfig.BucketConfig buckets = mock(MinioConfig.BucketConfig.class);
        when(minioConfig.getBucket()).thenReturn(buckets);
        when(buckets.getSubmissions()).thenReturn("submissions-bucket");
        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));
        when(minioService.downloadFile("submissions-bucket", "submissions/test.zip"))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        // Act
        var stream = lecturerAppealService.downloadSubmission(lecturerId, appealId);

        // Assert
        assertNotNull(stream);
        verify(minioService).downloadFile("submissions-bucket", "submissions/test.zip");
    }

    @Test
    @DisplayName("[A] downloadSubmission - File path là null -> Throw IllegalStateException")
    void downloadSubmission_NullFilePath_ThrowsException() {
        // Arrange
        UUID lecturerId = UUID.randomUUID();
        UUID appealId = UUID.randomUUID();
        Appeal appeal = buildAssignedAppeal(appealId, lecturerId, AppealStatus.PROCESSING);
        appeal.getSubmission().setFilePath(null);

        when(appealRepository.findById(appealId)).thenReturn(Optional.of(appeal));

        // Act & Assert
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> lecturerAppealService.downloadSubmission(lecturerId, appealId));
        assertTrue(ex.getMessage().contains("Không tìm thấy file"));
    }
}
