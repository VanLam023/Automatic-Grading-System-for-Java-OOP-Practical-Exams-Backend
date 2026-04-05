package agsfjope.backend.application.staffdashboardservices;

import agsfjope.backend.application.dtos.responses.staffdashboard.GradeDistributionResponse;
import agsfjope.backend.application.dtos.responses.staffdashboard.PendingAppealResponse;
import agsfjope.backend.application.dtos.responses.staffdashboard.RecentExamResponse;
import agsfjope.backend.application.dtos.responses.staffdashboard.StaffDashboardOverviewResponse;
import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.entities.Block;
import agsfjope.backend.core.entities.Exam;
import agsfjope.backend.core.entities.Submission;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.enums.ExamStatus;
import agsfjope.backend.core.enums.SubmissionStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.exam.ExamRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit Tests cho StaffDashboardServiceImpl
 * Pattern: AAA (Arrange - Act - Assert)
 * Đảm bảo 100% test coverage cho 4 public methods.
 */
@ExtendWith(MockitoExtension.class)
class StaffDashboardServiceImplTest {

    @Mock private ExamRepository examRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private GradingResultRepository gradingResultRepository;
    @Mock private AppealRepository appealRepository;

    @InjectMocks
    private StaffDashboardServiceImpl staffDashboardService;

    // =========================================================================
    // getOverview()
    // =========================================================================

    @Test
    @DisplayName("[N] getOverview - semester null (Không filter) -> Gọi repository dạng unfiltered")
    void getOverview_NullSemester_ReturnsUnfilteredStats() {
        // Arrange
        when(examRepository.countByStatusAndDeletedAtIsNull(ExamStatus.ONGOING)).thenReturn(2L);
        when(submissionRepository.count()).thenReturn(100L);
        when(submissionRepository.countByStatus(SubmissionStatus.GRADED)).thenReturn(80L);
        when(appealRepository.countByStatus(AppealStatus.PENDING.name())).thenReturn(5L);

        // Act
        StaffDashboardOverviewResponse response = staffDashboardService.getOverview(null);

        // Assert
        assertNotNull(response);
        assertEquals(2L, response.getActiveExams());
        assertEquals(100L, response.getTotalSubmissions());
        assertEquals(80L, response.getGradedSubmissions());
        assertEquals(5L, response.getPendingAppeals());

        verify(examRepository, never()).countByStatusAndDeletedAtIsNullAndSemester(any(), any());
        verify(submissionRepository, never()).countBySemester(any());
        verify(submissionRepository, never()).countByStatusAndSemester(any(), any());
        verify(appealRepository, never()).countByStatusAndSemester(any(), any());
    }

    @Test
    @DisplayName("[N] getOverview - có semester (Có filter) -> Gọi repository dạng filtered")
    void getOverview_WithValidSemester_ReturnsFilteredStats() {
        // Arrange
        String semester = "FA25";
        when(examRepository.countByStatusAndDeletedAtIsNullAndSemester(ExamStatus.ONGOING, semester)).thenReturn(1L);
        when(submissionRepository.countBySemester(semester)).thenReturn(50L);
        when(submissionRepository.countByStatusAndSemester(SubmissionStatus.GRADED, semester)).thenReturn(40L);
        when(appealRepository.countByStatusAndSemester(AppealStatus.PENDING.name(), semester)).thenReturn(2L);

        // Act
        StaffDashboardOverviewResponse response = staffDashboardService.getOverview(semester);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getActiveExams());
        assertEquals(50L, response.getTotalSubmissions());
        assertEquals(40L, response.getGradedSubmissions());
        assertEquals(2L, response.getPendingAppeals());

        verify(examRepository, never()).countByStatusAndDeletedAtIsNull(any());
        verify(submissionRepository, never()).count();
        verify(submissionRepository, never()).countByStatus(any());
        verify(appealRepository, never()).countByStatus(any());
    }

    // =========================================================================
    // getRecentExams()
    // =========================================================================

    @Test
    @DisplayName("[N] getRecentExams - semester null -> Trả về danh sách unfiltered")
    void getRecentExams_NullSemester_ReturnsUnfilteredExams() {
        // Arrange
        int limit = 10;
        PageRequest page = PageRequest.of(0, limit);
        Exam exam = Exam.builder()
                .examId(UUID.randomUUID())
                .name("Test Exam")
                .semester("SP25")
                .status(ExamStatus.ONGOING)
                .build();
        when(examRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc(page)).thenReturn(List.of(exam));

        // Act
        List<RecentExamResponse> response = staffDashboardService.getRecentExams(limit, " ");

        // Assert
        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("Test Exam", response.get(0).getName());
        assertEquals("SP25", response.get(0).getSemester());
        assertEquals(ExamStatus.ONGOING, response.get(0).getStatus());

        verify(examRepository, never()).findAllBySemesterAndDeletedAtIsNullOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("[N] getRecentExams - có semester -> Trả về danh sách filtered")
    void getRecentExams_WithSemester_ReturnsFilteredExams() {
        // Arrange
        int limit = 5;
        String semester = "FA25";
        PageRequest page = PageRequest.of(0, limit);
        Exam exam = Exam.builder()
                .examId(UUID.randomUUID())
                .name("Test Exam FA25")
                .semester(semester)
                .status(ExamStatus.COMPLETED)
                .build();
        when(examRepository.findAllBySemesterAndDeletedAtIsNullOrderByCreatedAtDesc(semester, page)).thenReturn(List.of(exam));

        // Act
        List<RecentExamResponse> response = staffDashboardService.getRecentExams(limit, semester);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("Test Exam FA25", response.get(0).getName());

        verify(examRepository, never()).findAllByDeletedAtIsNullOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("[B] getRecentExams - Trả về rỗng khi không có exam (Boundary)")
    void getRecentExams_NoExamsFound_ReturnsEmptyList() {
        // Arrange
        when(examRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc(any())).thenReturn(Collections.emptyList());

        // Act
        List<RecentExamResponse> response = staffDashboardService.getRecentExams(10, null);

        // Assert
        assertNotNull(response);
        assertTrue(response.isEmpty());
    }

    // =========================================================================
    // getGradeDistribution()
    // =========================================================================

    @Test
    @DisplayName("[N] getGradeDistribution - semester null -> Lấy theo tổng unfiltered, tính % chính xác")
    void getGradeDistribution_NullSemester_CalculatesPercentagesCorrectly() {
        // Arrange
        when(gradingResultRepository.countAll()).thenReturn(100L); // totalGraded = 100

        // mock các buckets (0-4: 10, 4-6: 20, 6-8: 40, 8-9: 20, 9-10: 10)
        when(gradingResultRepository.countByScoreRange(new BigDecimal("0.00"), new BigDecimal("4.00"))).thenReturn(15L);
        when(gradingResultRepository.countByScoreRange(new BigDecimal("4.00"), new BigDecimal("6.00"))).thenReturn(25L);
        when(gradingResultRepository.countByScoreRange(new BigDecimal("6.00"), new BigDecimal("8.00"))).thenReturn(45L);
        when(gradingResultRepository.countByScoreRange(new BigDecimal("8.00"), new BigDecimal("9.00"))).thenReturn(10L);
        when(gradingResultRepository.countByScoreRange(new BigDecimal("9.00"), new BigDecimal("10.01"))).thenReturn(5L);

        // Act
        GradeDistributionResponse response = staffDashboardService.getGradeDistribution(null);

        // Assert
        assertNotNull(response);
        assertEquals(100L, response.getTotalGraded());
        assertEquals(5, response.getRanges().size());
        
        // check % (15/100 -> 15.0%, 25/100 -> 25.0%, v.v)
        assertEquals(15.0, response.getRanges().get(0).getPercentage());
        assertEquals(25.0, response.getRanges().get(1).getPercentage());
        assertEquals(45.0, response.getRanges().get(2).getPercentage());
        assertEquals(10.0, response.getRanges().get(3).getPercentage());
        assertEquals(5.0, response.getRanges().get(4).getPercentage());
    }

    @Test
    @DisplayName("[N] getGradeDistribution - có semester -> Lấy theo filtered method")
    void getGradeDistribution_WithSemester_CallsFilteredMethods() {
        // Arrange
        String semester = "FA25";
        when(gradingResultRepository.countAllBySemester(semester)).thenReturn(10L); 
        when(gradingResultRepository.countByScoreRangeAndSemester(any(BigDecimal.class), any(BigDecimal.class), eq(semester)))
                .thenReturn(2L); // 5 ranges, each count = 2 -> total 10

        // Act
        GradeDistributionResponse response = staffDashboardService.getGradeDistribution(semester);

        // Assert
        assertNotNull(response);
        assertEquals(10L, response.getTotalGraded());
        assertEquals(5, response.getRanges().size());
        
        // each should be 20.0%
        for(GradeDistributionResponse.ScoreRange range : response.getRanges()) {
            assertEquals(20.0, range.getPercentage());
        }

        verify(gradingResultRepository, never()).countAll();
        verify(gradingResultRepository, never()).countByScoreRange(any(), any());
    }

    @Test
    @DisplayName("[B] getGradeDistribution - totalGraded = 0 -> percentages = 0.0, không bị Exception chia cho 0")
    void getGradeDistribution_TotalGradedZero_PercentagesAreZero() {
        // Arrange
        when(gradingResultRepository.countAll()).thenReturn(0L); 
        when(gradingResultRepository.countByScoreRange(any(BigDecimal.class), any(BigDecimal.class))).thenReturn(0L);

        // Act
        GradeDistributionResponse response = staffDashboardService.getGradeDistribution(null);

        // Assert
        assertNotNull(response);
        assertEquals(0L, response.getTotalGraded());
        for(GradeDistributionResponse.ScoreRange range : response.getRanges()) {
            assertEquals(0.0, range.getPercentage());
        }
    }

    // =========================================================================
    // getPendingAppeals()
    // =========================================================================

    @Test
    @DisplayName("[N] getPendingAppeals - semester null -> Map đầy đủ giá trị student, exam name")
    void getPendingAppeals_NullSemester_ReturnsMappedAppeals() {
        // Arrange
        int limit = 10;
        PageRequest page = PageRequest.of(0, limit);
        
        User student = User.builder().fullName("Lam Tran Van").mssv("SE173173").build();
        Exam exam = Exam.builder().name("Final Exam").build();
        Block block = Block.builder().exam(exam).build();
        Submission submission = Submission.builder().block(block).build();
        
        Appeal appeal = Appeal.builder()
                .appealId(UUID.randomUUID())
                .student(student)
                .submission(submission)
                .status(AppealStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();

        when(appealRepository.findPendingAndProcessingOrderByCreatedAtDesc(page)).thenReturn(List.of(appeal));

        // Act
        List<PendingAppealResponse> response = staffDashboardService.getPendingAppeals(limit, "  "); // blank parameter treats as null

        // Assert
        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("Lam Tran Van", response.get(0).getStudentName());
        assertEquals("SE173173", response.get(0).getStudentMssv());
        assertEquals("Final Exam", response.get(0).getExamName());
        assertEquals(AppealStatus.PENDING, response.get(0).getStatus());

        verify(appealRepository, never()).findPendingAndProcessingBySemesterOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("[A] getPendingAppeals - Appeal thiếu references (student = null, submission = null) Handles gracefully")
    void getPendingAppeals_AppealsWithNullDependencies_HandlesGracefully() {
        // Arrange
        int limit = 10;
        PageRequest page = PageRequest.of(0, limit);
        
        // Appeal with NO student, NO submission
        Appeal appeal = Appeal.builder()
                .appealId(UUID.randomUUID())
                .status(AppealStatus.PROCESSING)
                .createdAt(OffsetDateTime.now())
                .build();

        when(appealRepository.findPendingAndProcessingOrderByCreatedAtDesc(page)).thenReturn(List.of(appeal));

        // Act
        List<PendingAppealResponse> response = staffDashboardService.getPendingAppeals(limit, null);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("", response.get(0).getStudentName());
        assertEquals("", response.get(0).getStudentMssv());
        assertEquals("", response.get(0).getExamName()); // Phải map là empty string nếu null
    }

    @Test
    @DisplayName("[N] getPendingAppeals - có semester -> Gọi hàm repository map kèm semester")
    void getPendingAppeals_WithSemester_CallsFilteredRepository() {
        // Arrange
        int limit = 5;
        String semester = "FA25";
        PageRequest page = PageRequest.of(0, limit);
        when(appealRepository.findPendingAndProcessingBySemesterOrderByCreatedAtDesc(semester, page)).thenReturn(Collections.emptyList());

        // Act
        List<PendingAppealResponse> response = staffDashboardService.getPendingAppeals(limit, semester);

        // Assert
        assertNotNull(response);
        assertTrue(response.isEmpty());

        verify(appealRepository).findPendingAndProcessingBySemesterOrderByCreatedAtDesc(semester, page);
        verify(appealRepository, never()).findPendingAndProcessingOrderByCreatedAtDesc(any());
    }
}
