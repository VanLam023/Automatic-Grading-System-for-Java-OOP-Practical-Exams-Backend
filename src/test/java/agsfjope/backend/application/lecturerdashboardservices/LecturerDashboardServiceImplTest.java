package agsfjope.backend.application.lecturerdashboardservices;

import agsfjope.backend.application.dtos.responses.lecturerdashboard.AssignedAppealResponse;
import agsfjope.backend.application.dtos.responses.lecturerdashboard.LecturerDashboardOverviewResponse;
import agsfjope.backend.application.dtos.responses.lecturerdashboard.ReviewStatsResponse;
import agsfjope.backend.application.dtos.responses.lecturerdashboard.UpcomingDeadlineResponse;
import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.entities.Block;
import agsfjope.backend.core.entities.Exam;
import agsfjope.backend.core.entities.Submission;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit Tests cho LecturerDashboardServiceImpl
 * Pattern: AAA (Arrange - Act - Assert)
 * Mức độ ưu tiên kiểm thử: 100% CC cho 4 public methods.
 */
@ExtendWith(MockitoExtension.class)
class LecturerDashboardServiceImplTest {

    @Mock
    private AppealRepository appealRepository;

    @InjectMocks
    private LecturerDashboardServiceImpl lecturerDashboardService;

    // =========================================================================
    // getOverview()
    // =========================================================================

    @Test
    @DisplayName("[N] getOverview - Happy Path")
    void getOverview_ValidLecturerId_ReturnsCorrectStats() {
        // Arrange
        UUID lecturerId = UUID.randomUUID();
        when(appealRepository.countByAssignedLecturerAndStatus(lecturerId, "PROCESSING")).thenReturn(5L);
        when(appealRepository.countCompletedReviewsByAssignedLecturer(lecturerId)).thenReturn(10L);
        when(appealRepository.countOverdueByAssignedLecturer(eq(lecturerId), any(OffsetDateTime.class))).thenReturn(2L);

        // Act
        LecturerDashboardOverviewResponse result = lecturerDashboardService.getOverview(lecturerId);

        // Assert
        assertNotNull(result);
        assertEquals(5L, result.getAssignedAppeals());
        assertEquals(10L, result.getCompletedReviews());
        assertEquals(2L, result.getOverdueAppeals());
    }

    // =========================================================================
    // getAssignedAppeals()
    // =========================================================================

    @Test
    @DisplayName("[N] getAssignedAppeals - Status là null hoặc blank -> Gọi hàm findAll unfiltered")
    void getAssignedAppeals_NullOrBlankStatus_ReturnsUnfiltered() {
        // Arrange
        UUID lecturerId = UUID.randomUUID();
        int limit = 10;
        User student = User.builder().fullName("Nguyen Van A").mssv("SE12345").build();
        Exam exam = Exam.builder().name("Final Java").build();
        Block block = Block.builder().exam(exam).name("Code1").build();
        Submission submission = Submission.builder().block(block).build();
        
        Appeal appeal = Appeal.builder()
                .appealId(UUID.randomUUID())
                .student(student)
                .submission(submission)
                .status(AppealStatus.PROCESSING)
                .assignedAt(OffsetDateTime.now())
                .deadlineAt(OffsetDateTime.now().plusDays(2))
                .build();
                
        when(appealRepository.findByAssignedLecturerOrderByAssignedAtDesc(eq(lecturerId), any(Pageable.class)))
                .thenReturn(List.of(appeal));

        // Act
        List<AssignedAppealResponse> result = lecturerDashboardService.getAssignedAppeals(lecturerId, limit, "   ");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Nguyen Van A", result.get(0).getStudentName());
        assertEquals("SE12345", result.get(0).getStudentMssv());
        assertEquals("Final Java", result.get(0).getExamName());
        assertEquals("Code1", result.get(0).getBlockName());
        assertEquals(AppealStatus.PROCESSING, result.get(0).getStatus());

        verify(appealRepository, never()).findByAssignedLecturerAndStatusOrderByAssignedAtDesc(any(), any(), any());
    }

    @Test
    @DisplayName("[N] getAssignedAppeals - Status có giá trị -> Gọi hàm filter by status có UpperCase")
    void getAssignedAppeals_WithStatus_ReturnsFiltered() {
        // Arrange
        UUID lecturerId = UUID.randomUUID();
        int limit = 5;
        String status = "pending";
        Appeal appeal = Appeal.builder()
                .appealId(UUID.randomUUID())
                .status(AppealStatus.PENDING)
                .build();

        when(appealRepository.findByAssignedLecturerAndStatusOrderByAssignedAtDesc(eq(lecturerId), eq("PENDING"), any(Pageable.class)))
                .thenReturn(List.of(appeal));

        // Act
        List<AssignedAppealResponse> result = lecturerDashboardService.getAssignedAppeals(lecturerId, limit, status);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(appealRepository, never()).findByAssignedLecturerOrderByAssignedAtDesc(any(), any());
    }

    @Test
    @DisplayName("[A] getAssignedAppeals - Appeal null relationships (student, submission, block, exam) -> Map fields dạng rỗng, tránh NPE")
    void getAssignedAppeals_AppealWithNullAssociations_MapsToEmptyStrings() {
        // Arrange
        UUID lecturerId = UUID.randomUUID();
        // Appeal with NO student and NO submission
        Appeal appeal = Appeal.builder().appealId(UUID.randomUUID()).build();
        
        when(appealRepository.findByAssignedLecturerOrderByAssignedAtDesc(eq(lecturerId), any(Pageable.class)))
                .thenReturn(List.of(appeal));

        // Act
        List<AssignedAppealResponse> result = lecturerDashboardService.getAssignedAppeals(lecturerId, 10, null);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("", result.get(0).getStudentName());
        assertEquals("", result.get(0).getStudentMssv());
        assertEquals("", result.get(0).getExamName());
        assertEquals("", result.get(0).getBlockName());
    }

    @Test
    @DisplayName("[B] getAssignedAppeals - Repo rỗng -> Trả về mảng rỗng")
    void getAssignedAppeals_EmptyListFound_ReturnsEmpty() {
        // Arrange
        when(appealRepository.findByAssignedLecturerOrderByAssignedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        List<AssignedAppealResponse> result = lecturerDashboardService.getAssignedAppeals(UUID.randomUUID(), 10, null);

        // Assert
        assertTrue(result.isEmpty());
    }

    // =========================================================================
    // getUpcomingDeadlines()
    // =========================================================================

    @Test
    @DisplayName("[N] getUpcomingDeadlines - Set urgency labels correct logic (Overdue -> CẦN XỬ LÝ NGAY, <=48h -> TRONG 2 NGÀY TỚI, >48h -> SẮP TỚI)")
    void getUpcomingDeadlines_ValidAppeals_SetsUrgencyLabelCorrectly() {
        // Arrange
        UUID lecturerId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        
        Appeal pastDue = Appeal.builder().deadlineAt(now.minusHours(1)).build(); // CẦN XỬ LÝ NGAY
        Appeal urgent = Appeal.builder().deadlineAt(now.plusHours(24)).build();  // TRONG 2 NGÀY TỚI
        Appeal farFuture = Appeal.builder().deadlineAt(now.plusHours(72)).build(); // SẮP TỚI

        when(appealRepository.findProcessingByAssignedLecturerOrderByDeadlineAsc(eq(lecturerId), any(Pageable.class)))
                .thenReturn(List.of(pastDue, urgent, farFuture));

        // Act
        List<UpcomingDeadlineResponse> result = lecturerDashboardService.getUpcomingDeadlines(lecturerId, 10);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("CẦN XỬ LÝ NGAY", result.get(0).getUrgencyLabel());
        assertEquals("TRONG 2 NGÀY TỚI", result.get(1).getUrgencyLabel());
        assertEquals("SẮP TỚI", result.get(2).getUrgencyLabel());
    }

    @Test
    @DisplayName("[B] getUpcomingDeadlines - Deadline null -> Mặc định là SẮP TỚI")
    void getUpcomingDeadlines_NullDeadline_ReturnsDefaultLabel() {
        // Arrange
        UUID lecturerId = UUID.randomUUID();
        Appeal appeal = Appeal.builder().deadlineAt(null).build();

        when(appealRepository.findProcessingByAssignedLecturerOrderByDeadlineAsc(eq(lecturerId), any(Pageable.class)))
                .thenReturn(List.of(appeal));

        // Act
        List<UpcomingDeadlineResponse> result = lecturerDashboardService.getUpcomingDeadlines(lecturerId, 10);

        // Assert
        assertNotNull(result);
        assertEquals("SẮP TỚI", result.get(0).getUrgencyLabel());
    }

    @Test
    @DisplayName("[A] getUpcomingDeadlines - Null association entity -> Map null thành rỗng")
    void getUpcomingDeadlines_NullAssociations_SetsEmptyNames() {
        // Arrange
        UUID lecturerId = UUID.randomUUID();
        Appeal appeal = Appeal.builder().build(); // Everything null

        when(appealRepository.findProcessingByAssignedLecturerOrderByDeadlineAsc(eq(lecturerId), any(Pageable.class)))
                .thenReturn(List.of(appeal));

        // Act
        List<UpcomingDeadlineResponse> result = lecturerDashboardService.getUpcomingDeadlines(lecturerId, 10);

        // Assert
        assertNotNull(result);
        assertEquals("", result.get(0).getStudentName());
        assertEquals("", result.get(0).getExamName());
    }

    // =========================================================================
    // getReviewStats()
    // =========================================================================

    @Test
    @DisplayName("[N] getReviewStats - total > 0 -> Logic làm tròn 1 chữ số thập phân chuẩn xác")
    void getReviewStats_ValidCounts_CalculatesPercentages() {
        // Arrange
        UUID lecturerId = UUID.randomUUID();
        when(appealRepository.countApprovedByAssignedLecturer(lecturerId)).thenReturn(1L);
        when(appealRepository.countDeniedByAssignedLecturer(lecturerId)).thenReturn(2L);
        when(appealRepository.countCompletedReviewsByAssignedLecturer(lecturerId)).thenReturn(3L);

        // Act
        ReviewStatsResponse result = lecturerDashboardService.getReviewStats(lecturerId);

        // Assert
        assertNotNull(result);
        assertEquals(3L, result.getTotalReviews());
        assertEquals(1L, result.getApprovedCount());
        assertEquals(2L, result.getDeniedCount());
        
        // 1/3 = 33.3%, 2/3 = 66.7%
        assertEquals(33.3, result.getApprovedPercentage());
        assertEquals(66.7, result.getDeniedPercentage());
    }

    @Test
    @DisplayName("[B] getReviewStats - total == 0 -> percentage là 0.0 để tránh Infinity/NaN")
    void getReviewStats_ZeroTotalReviews_ReturnsZeroPercentages() {
        // Arrange
        UUID lecturerId = UUID.randomUUID();
        when(appealRepository.countApprovedByAssignedLecturer(lecturerId)).thenReturn(0L);
        when(appealRepository.countDeniedByAssignedLecturer(lecturerId)).thenReturn(0L);
        when(appealRepository.countCompletedReviewsByAssignedLecturer(lecturerId)).thenReturn(0L);

        // Act
        ReviewStatsResponse result = lecturerDashboardService.getReviewStats(lecturerId);

        // Assert
        assertNotNull(result);
        assertEquals(0L, result.getTotalReviews());
        assertEquals(0.0, result.getApprovedPercentage());
        assertEquals(0.0, result.getDeniedPercentage());
    }
}
