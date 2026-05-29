package agsfjope.backend.application.examstatisticsservices.impl;

import agsfjope.backend.application.dtos.responses.statistics.BlockStatisticsResponse;
import agsfjope.backend.application.dtos.responses.statistics.BlockStatisticsResponse.*;
import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.enums.CriterionType;
import agsfjope.backend.core.enums.TestCaseStatus;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.block.BlockRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.core.repositories.grading.CriteriaResultRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.grading.TestCaseResultRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests cho ExamStatisticsServiceImpl
 * Pattern: AAA (Arrange - Act - Assert)
 */
@ExtendWith(MockitoExtension.class)
class ExamStatisticsServiceImplTest {

    @Mock private BlockRepository blockRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private GradingResultRepository gradingResultRepository;
    @Mock private CriteriaResultRepository criteriaResultRepository;
    @Mock private TestCaseResultRepository testCaseResultRepository;
    @Mock private AppealRepository appealRepository;
    @Mock private SystemConfigRepository systemConfigRepository;

    @InjectMocks
    private ExamStatisticsServiceImpl examStatisticsService;

    // =========================================================================
    // Helpers
    // =========================================================================

    private void mockScoreRepo(UUID blockId, long graded, Double avg, Double max, Double min,
                                long pass, long fail) {
        when(gradingResultRepository.countGradedByBlockId(blockId)).thenReturn(graded);
        when(gradingResultRepository.avgScoreByBlockId(blockId)).thenReturn(avg);
        when(gradingResultRepository.maxScoreByBlockId(blockId)).thenReturn(max);
        when(gradingResultRepository.minScoreByBlockId(blockId)).thenReturn(min);
        when(gradingResultRepository.countPassByBlockId(blockId)).thenReturn(pass);
        when(gradingResultRepository.countFailByBlockId(blockId)).thenReturn(fail);
        when(gradingResultRepository.countByBlockIdAndScoreRange(eq(blockId), any(), any())).thenReturn(0L);
    }

    private void mockAppealRepo(UUID blockId) {
        when(appealRepository.countByBlockId(blockId)).thenReturn(0L);
        when(appealRepository.countByBlockIdAndStatus(eq(blockId), anyString())).thenReturn(0L);
    }

    private CriteriaResult mockCriteriaResult(CriterionType type, String desc, double earned, double max, boolean passed) {
        CriteriaResult cr = mock(CriteriaResult.class);
        GradingCriteria c = mock(GradingCriteria.class);
        lenient().when(c.getCriterionType()).thenReturn(type);
        lenient().when(c.getDescription()).thenReturn(desc);
        lenient().when(c.getMaxScore()).thenReturn(BigDecimal.valueOf(max));
        lenient().when(cr.getCriteria()).thenReturn(c);
        lenient().when(cr.getEarnedScore()).thenReturn(BigDecimal.valueOf(earned));
        lenient().when(cr.isPassed()).thenReturn(passed);
        
        Answer a = mock(Answer.class);
        lenient().when(a.getAnswerId()).thenReturn(UUID.randomUUID());
        lenient().when(cr.getAnswer()).thenReturn(a);
        
        return cr;
    }

    private TestCaseResult mockTestCaseResult(int qNum, int tcNum, double earned, TestCaseStatus status) {
        TestCaseResult tcr = mock(TestCaseResult.class);
        TestCase tc = mock(TestCase.class);
        Question q = mock(Question.class);
        
        lenient().when(q.getQuestionNumber()).thenReturn(qNum);
        lenient().when(tc.getQuestion()).thenReturn(q);
        lenient().when(tc.getTestCaseNumber()).thenReturn(tcNum);
        lenient().when(tcr.getTestCase()).thenReturn(tc);
        lenient().when(tcr.getScoreEarned()).thenReturn(BigDecimal.valueOf(earned));
        lenient().when(tcr.getStatus()).thenReturn(status);
        
        return tcr;
    }

    // =========================================================================
    // 1. getBlockStatistics — Validation
    // =========================================================================

    @Test
    @DisplayName("[A] getBlockStatistics - blockId không tồn tại -> Throw NotFoundException")
    void getBlockStatistics_BlockNotFound_ThrowsNotFoundException() {
        // Arrange
        UUID examId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        when(blockRepository.existsById(blockId)).thenReturn(false);

        // Act & Assert
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> examStatisticsService.getBlockStatistics(examId, blockId));
        assertTrue(ex.getMessage().contains("Block không tồn tại"));
    }

    @Test
    @DisplayName("[A] getBlockStatistics - Block tồn tại nhưng không thuộc examId -> Throw NotFoundException")
    void getBlockStatistics_BlockNotBelongToExam_ThrowsNotFoundException() {
        // Arrange
        UUID examId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        when(blockRepository.existsById(blockId)).thenReturn(true);
        when(blockRepository.existsByBlockIdAndExam_ExamId(blockId, examId)).thenReturn(false);

        // Act & Assert
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> examStatisticsService.getBlockStatistics(examId, blockId));
        assertTrue(ex.getMessage().contains("Block không thuộc kỳ thi này"));
    }

    // =========================================================================
    // 2. getBlockStatistics — Score Analysis
    // =========================================================================

    @Test
    @DisplayName("[N] getBlockStatistics - Block hợp lệ, có 10 bài chấm -> Trả về scoreAnalysis đầy đủ")
    void getBlockStatistics_ValidBlockWithGradedSubmissions_ReturnsScoreAnalysis() {
        // Arrange
        UUID examId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        when(blockRepository.existsById(blockId)).thenReturn(true);
        when(blockRepository.existsByBlockIdAndExam_ExamId(blockId, examId)).thenReturn(true);
        when(submissionRepository.countByBlock_BlockId(blockId)).thenReturn(10L);
        mockScoreRepo(blockId, 10L, 7.5, 10.0, 4.0, 8L, 2L);
        when(criteriaResultRepository.findAllByBlockId(blockId)).thenReturn(Collections.emptyList());
        when(testCaseResultRepository.findAllByBlockId(blockId)).thenReturn(Collections.emptyList());
        mockAppealRepo(blockId);
        when(systemConfigRepository.findByConfigKey("APPEAL_FEE")).thenReturn(Optional.empty());

        // Act
        BlockStatisticsResponse response = examStatisticsService.getBlockStatistics(examId, blockId);

        // Assert
        assertNotNull(response);
        assertEquals(10L, response.getTotalSubmissions());
        assertEquals(10L, response.getGradedSubmissions());

        var score = response.getScoreAnalysis();
        assertEquals(new BigDecimal("7.50"), score.getAvgScore());
        assertEquals(new BigDecimal("10.00"), score.getMaxScore());
        assertEquals(new BigDecimal("4.00"), score.getMinScore());
        assertEquals(8L, score.getPassCount());
        assertEquals(2L, score.getFailCount());
        assertEquals(80.0, score.getPassRate());
        assertEquals(20.0, score.getFailRate());
    }

    @Test
    @DisplayName("[B] getBlockStatistics - Chưa có bài chấm nào (gradedCount=0) -> Avg/Max/Min = 0, passRate = 0")
    void getBlockStatistics_NoGradedSubmissions_ReturnsZeroScores() {
        // Arrange
        UUID examId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        when(blockRepository.existsById(blockId)).thenReturn(true);
        when(blockRepository.existsByBlockIdAndExam_ExamId(blockId, examId)).thenReturn(true);
        when(submissionRepository.countByBlock_BlockId(blockId)).thenReturn(5L);
        mockScoreRepo(blockId, 0L, null, null, null, 0L, 0L);
        when(criteriaResultRepository.findAllByBlockId(blockId)).thenReturn(Collections.emptyList());
        when(testCaseResultRepository.findAllByBlockId(blockId)).thenReturn(Collections.emptyList());
        mockAppealRepo(blockId);
        when(systemConfigRepository.findByConfigKey("APPEAL_FEE")).thenReturn(Optional.empty());

        // Act
        BlockStatisticsResponse response = examStatisticsService.getBlockStatistics(examId, blockId);

        // Assert
        var score = response.getScoreAnalysis();
        assertEquals(0, score.getAvgScore().compareTo(BigDecimal.ZERO), "avgScore should be 0");
        assertEquals(0, score.getMaxScore().compareTo(BigDecimal.ZERO), "maxScore should be 0");
        assertEquals(0, score.getMinScore().compareTo(BigDecimal.ZERO), "minScore should be 0");
        assertEquals(0.0, score.getPassRate());
        assertEquals(0.0, score.getFailRate());
    }

    // =========================================================================
    // 3. getBlockStatistics — OOP Analysis & Test Cases
    // =========================================================================

    @Test
    @DisplayName("[N] getBlockStatistics - Có criteria results -> Phân tích OOP chính xác")
    void getBlockStatistics_WithCriteriaResults_ParsesOopViolations() {
        // Arrange
        UUID examId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        CriteriaResult cr1 = mockCriteriaResult(CriterionType.FIELD_CHECK, "Encapsulation Field", 0.0, 1.0, false);
        CriteriaResult cr2 = mockCriteriaResult(CriterionType.EXTENDS_CHECK, "Inheritance Extends", 2.0, 2.0, true);

        when(blockRepository.existsById(blockId)).thenReturn(true);
        when(blockRepository.existsByBlockIdAndExam_ExamId(blockId, examId)).thenReturn(true);
        when(submissionRepository.countByBlock_BlockId(blockId)).thenReturn(2L);
        mockScoreRepo(blockId, 2L, 7.0, 8.0, 6.0, 2L, 0L);
        when(criteriaResultRepository.findAllByBlockId(blockId)).thenReturn(List.of(cr1, cr2));
        when(testCaseResultRepository.findAllByBlockId(blockId)).thenReturn(Collections.emptyList());
        mockAppealRepo(blockId);
        when(systemConfigRepository.findByConfigKey("APPEAL_FEE")).thenReturn(Optional.empty());

        // Act
        BlockStatisticsResponse response = examStatisticsService.getBlockStatistics(examId, blockId);

        // Assert
        var oop = response.getAiOopAnalysis();
        // sumEarned = 2.0, sumMax = 3.0 -> 2/3 * 10 = 6.67
        assertEquals(new BigDecimal("6.67"), oop.getAvgOopScore());
        assertEquals(1L, oop.getOopViolatedCount());
        assertEquals(1L, oop.getEncapsulationViolations());
        assertEquals(0L, oop.getInheritanceViolations());
    }

    @Test
    @DisplayName("[N] getBlockStatistics - Bổ sung thống kê chi tiết Test Cases hoạt động đúng")
    void getBlockStatistics_WithTestCaseResults_AggregatesTestCaseStats() {
        // Arrange
        UUID examId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        // 3 testcase runs:
        // Câu 1 - Test Case 1: 1 pass (1.0đ), 1 fail (0.0đ) -> 50% fail, avg = 0.5đ
        // Câu 1 - Test Case 2: 1 fail (0.0đ) -> 100% fail, avg = 0.0đ
        TestCaseResult tcr1 = mockTestCaseResult(1, 1, 1.0, TestCaseStatus.PASS_TESTCASE);
        TestCaseResult tcr2 = mockTestCaseResult(1, 1, 0.0, TestCaseStatus.FAIL_TESTCASE);
        TestCaseResult tcr3 = mockTestCaseResult(1, 2, 0.0, TestCaseStatus.TIMEOUT);

        when(blockRepository.existsById(blockId)).thenReturn(true);
        when(blockRepository.existsByBlockIdAndExam_ExamId(blockId, examId)).thenReturn(true);
        when(submissionRepository.countByBlock_BlockId(blockId)).thenReturn(2L);
        mockScoreRepo(blockId, 2L, 6.0, 7.0, 5.0, 2L, 0L);
        when(criteriaResultRepository.findAllByBlockId(blockId)).thenReturn(Collections.emptyList());
        when(testCaseResultRepository.findAllByBlockId(blockId)).thenReturn(List.of(tcr1, tcr2, tcr3));
        mockAppealRepo(blockId);
        when(systemConfigRepository.findByConfigKey("APPEAL_FEE")).thenReturn(Optional.empty());

        // Act
        BlockStatisticsResponse response = examStatisticsService.getBlockStatistics(examId, blockId);

        // Assert
        assertNotNull(response);
        var tcStats = response.getTestCaseStats();
        assertEquals(2, tcStats.size());

        // Do sắp xếp tỷ lệ lỗi giảm dần, Câu 1 - Test Case 2 (100%) đứng đầu, Câu 1 - Test Case 1 (50%) thứ hai
        var stat1 = tcStats.get(0);
        assertEquals("Câu 1 - Test Case 2", stat1.getName());
        assertEquals(100.0, stat1.getFailureRate());
        assertEquals(0, stat1.getAvgScore().compareTo(BigDecimal.ZERO));
        assertEquals(1L, stat1.getSampleSize());

        var stat2 = tcStats.get(1);
        assertEquals("Câu 1 - Test Case 1", stat2.getName());
        assertEquals(50.0, stat2.getFailureRate());
        assertEquals(new BigDecimal("0.50"), stat2.getAvgScore());
        assertEquals(2L, stat2.getSampleSize());
    }

    // =========================================================================
    // 4. getBlockStatistics — Appeal & Financial
    // =========================================================================

    @Test
    @DisplayName("[N] getBlockStatistics - Có 3 đơn phúc khảo (2 approved, 1 denied) -> Tính revenue đúng")
    void getBlockStatistics_WithAppeals_CalculatesFinancialCorrectly() {
        // Arrange
        UUID examId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        when(blockRepository.existsById(blockId)).thenReturn(true);
        when(blockRepository.existsByBlockIdAndExam_ExamId(blockId, examId)).thenReturn(true);
        when(submissionRepository.countByBlock_BlockId(blockId)).thenReturn(5L);
        mockScoreRepo(blockId, 5L, 7.0, 9.0, 4.5, 4L, 1L);
        when(criteriaResultRepository.findAllByBlockId(blockId)).thenReturn(Collections.emptyList());
        when(testCaseResultRepository.findAllByBlockId(blockId)).thenReturn(Collections.emptyList());

        // Appeal data
        when(appealRepository.countByBlockId(blockId)).thenReturn(3L);
        when(appealRepository.countByBlockIdAndStatus(blockId, "PENDING")).thenReturn(0L);
        when(appealRepository.countByBlockIdAndStatus(blockId, "PROCESSING")).thenReturn(0L);
        when(appealRepository.countByBlockIdAndStatus(blockId, "APPROVED")).thenReturn(2L);
        when(appealRepository.countByBlockIdAndStatus(blockId, "DENIED")).thenReturn(1L);
        when(appealRepository.countByBlockIdAndStatus(blockId, "PENDING_PAYMENT")).thenReturn(0L);
        when(appealRepository.countByBlockIdAndStatus(blockId, "CANCELLED")).thenReturn(0L);
        when(systemConfigRepository.findByConfigKey("APPEAL_FEE")).thenReturn(Optional.empty()); // default 200k

        // Act
        BlockStatisticsResponse response = examStatisticsService.getBlockStatistics(examId, blockId);

        // Assert
        var financial = response.getAppealFinancial();
        assertEquals(3L, financial.getTotalAppeals());
        assertEquals(2L, financial.getApprovedCount());
        assertEquals(1L, financial.getDeniedCount());

        // approvedRate = 2/(2+1)*100 = 66.7%
        assertEquals(66.7, financial.getApprovedRate());
        // totalFees = 3 * 200000 = 600000 (3 paid, none cancelled/pending_payment)
        assertEquals(new BigDecimal("600000"), financial.getTotalFeesCollected());
        // totalRefunded = 2 * 200000 = 400000 (2 approved)
        assertEquals(new BigDecimal("400000"), financial.getTotalRefunded());
        // netRevenue = 600000 - 400000 = 200000
        assertEquals(new BigDecimal("200000"), financial.getNetRevenue());
    }

    @Test
    @DisplayName("[N] getBlockStatistics - Config có APPEAL_FEE=150000 -> Dùng fee từ systemConfig")
    void getBlockStatistics_CustomAppealFeeFromConfig_UsesConfigValue() {
        // Arrange
        UUID examId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        agsfjope.backend.core.entities.SystemConfig feeConfig = new agsfjope.backend.core.entities.SystemConfig();
        feeConfig.setConfigValue("150000");

        when(blockRepository.existsById(blockId)).thenReturn(true);
        when(blockRepository.existsByBlockIdAndExam_ExamId(blockId, examId)).thenReturn(true);
        when(submissionRepository.countByBlock_BlockId(blockId)).thenReturn(1L);
        mockScoreRepo(blockId, 1L, 8.0, 8.0, 8.0, 1L, 0L);
        when(criteriaResultRepository.findAllByBlockId(blockId)).thenReturn(Collections.emptyList());
        when(testCaseResultRepository.findAllByBlockId(blockId)).thenReturn(Collections.emptyList());
        when(appealRepository.countByBlockId(blockId)).thenReturn(1L);
        when(appealRepository.countByBlockIdAndStatus(eq(blockId), anyString())).thenReturn(0L);
        when(appealRepository.countByBlockIdAndStatus(blockId, "APPROVED")).thenReturn(1L);
        when(systemConfigRepository.findByConfigKey("APPEAL_FEE")).thenReturn(Optional.of(feeConfig));

        // Act
        BlockStatisticsResponse response = examStatisticsService.getBlockStatistics(examId, blockId);

        // Assert - fee = 150000, paidAppeals = 1, approved = 1
        // totalFees = 1 * 150000 = 150000
        // totalRefunded = 1 * 150000 = 150000
        // netRevenue = 0
        var financial = response.getAppealFinancial();
        assertEquals(new BigDecimal("150000"), financial.getTotalFeesCollected());
        assertEquals(new BigDecimal("150000"), financial.getTotalRefunded());
        assertEquals(0, financial.getNetRevenue().compareTo(BigDecimal.ZERO));
    }
}
