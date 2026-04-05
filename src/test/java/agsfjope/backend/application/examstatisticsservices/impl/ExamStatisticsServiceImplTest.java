package agsfjope.backend.application.examstatisticsservices.impl;

import agsfjope.backend.application.dtos.responses.statistics.BlockStatisticsResponse;
import agsfjope.backend.core.entities.AIReview;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.block.BlockRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.core.repositories.grading.AIReviewRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    @Mock private AIReviewRepository aiReviewRepository;
    @Mock private AppealRepository appealRepository;
    @Mock private SystemConfigRepository systemConfigRepository;

    // Dùng ObjectMapper thật để parse JSON trong test
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ExamStatisticsServiceImpl examStatisticsService;

    // =========================================================================
    // Helper: inject real ObjectMapper
    // =========================================================================
    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // InjectMocks dùng @Mock ObjectMapper (null) → phải inject thật
        try {
            var field = ExamStatisticsServiceImpl.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(examStatisticsService, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private AIReview buildAIReview(String rawJson, BigDecimal oopScore, boolean isOopViolated) {
        AIReview r = new AIReview();
        r.setAiReviewId(UUID.randomUUID());
        r.setRawResponse(rawJson);
        r.setOopScore(oopScore);
        r.setIsOopViolated(isOopViolated);
        return r;
    }

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
        when(aiReviewRepository.findAllByBlockId(blockId)).thenReturn(Collections.emptyList());
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
        assertEquals(10, score.getDistribution().size()); // 10 buckets
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
        when(aiReviewRepository.findAllByBlockId(blockId)).thenReturn(Collections.emptyList());
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
    // 3. getBlockStatistics — AI OOP Analysis
    // =========================================================================

    @Test
    @DisplayName("[N] getBlockStatistics - Có 1 AIReview với violations -> Phân tích OOP chính xác")
    void getBlockStatistics_WithAIReviews_ParsesOopViolations() {
        // Arrange
        UUID examId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        // JSON rawResponse: encapsulation=1 (< 2 → violated), others >= 2
        String rawJson = """
                {
                  "encapsulation": 1,
                  "inheritance": 2,
                  "polymorphism": 2,
                  "designQuality": 2,
                  "codeIntegrity": 1.5,
                  "hardCodedValues": ["magic_number"]
                }
                """;
        AIReview review = buildAIReview(rawJson, new BigDecimal("6.0"), true);

        when(blockRepository.existsById(blockId)).thenReturn(true);
        when(blockRepository.existsByBlockIdAndExam_ExamId(blockId, examId)).thenReturn(true);
        when(submissionRepository.countByBlock_BlockId(blockId)).thenReturn(1L);
        mockScoreRepo(blockId, 1L, 6.0, 6.0, 6.0, 1L, 0L);
        when(aiReviewRepository.findAllByBlockId(blockId)).thenReturn(List.of(review));
        mockAppealRepo(blockId);
        when(systemConfigRepository.findByConfigKey("APPEAL_FEE")).thenReturn(Optional.empty());

        // Act
        BlockStatisticsResponse response = examStatisticsService.getBlockStatistics(examId, blockId);

        // Assert
        var oop = response.getAiOopAnalysis();
        assertEquals(new BigDecimal("6.00"), oop.getAvgOopScore());
        assertEquals(1L, oop.getOopViolatedCount());    // isOopViolated = true
        assertEquals(1L, oop.getHardCodeCount());        // hardCodedValues has items
        assertEquals(1L, oop.getEncapsulationViolations()); // score 1 < 2
        assertEquals(0L, oop.getInheritanceViolations());    // score 2 = threshold (NOT < 2)
        assertEquals(0L, oop.getPolymorphismViolations());
        assertEquals(0L, oop.getDesignQualityViolations());
        assertEquals(1L, oop.getCodeIntegrityViolations()); // score 1.5 < 2
    }

    @Test
    @DisplayName("[N] getBlockStatistics - Không có AIReview nào -> Trả về AiOopAnalysis toàn 0")
    void getBlockStatistics_NoAIReviews_ReturnsZeroOopAnalysis() {
        // Arrange
        UUID examId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        when(blockRepository.existsById(blockId)).thenReturn(true);
        when(blockRepository.existsByBlockIdAndExam_ExamId(blockId, examId)).thenReturn(true);
        when(submissionRepository.countByBlock_BlockId(blockId)).thenReturn(3L);
        mockScoreRepo(blockId, 3L, 7.0, 9.0, 5.0, 3L, 0L);
        when(aiReviewRepository.findAllByBlockId(blockId)).thenReturn(Collections.emptyList());
        mockAppealRepo(blockId);
        when(systemConfigRepository.findByConfigKey("APPEAL_FEE")).thenReturn(Optional.empty());

        // Act
        BlockStatisticsResponse response = examStatisticsService.getBlockStatistics(examId, blockId);

        // Assert
        var oop = response.getAiOopAnalysis();
        assertEquals(0, oop.getAvgOopScore().compareTo(BigDecimal.ZERO));
        assertEquals(0L, oop.getOopViolatedCount());
        assertEquals(0L, oop.getHardCodeCount());
        assertEquals(0, oop.getEncapsulationViolations());
    }

    @Test
    @DisplayName("[B] getBlockStatistics - AIReview có rawResponse null/blank -> Parse bỏ qua, không crash")
    void getBlockStatistics_AIReviewWithNullRaw_SkipsParseGracefully() {
        // Arrange
        UUID examId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        AIReview nullRawReview = buildAIReview(null, new BigDecimal("7.0"), false);
        AIReview blankRawReview = buildAIReview("   ", new BigDecimal("8.0"), true);

        when(blockRepository.existsById(blockId)).thenReturn(true);
        when(blockRepository.existsByBlockIdAndExam_ExamId(blockId, examId)).thenReturn(true);
        when(submissionRepository.countByBlock_BlockId(blockId)).thenReturn(2L);
        mockScoreRepo(blockId, 2L, 7.5, 8.0, 7.0, 2L, 0L);
        when(aiReviewRepository.findAllByBlockId(blockId)).thenReturn(List.of(nullRawReview, blankRawReview));
        mockAppealRepo(blockId);
        when(systemConfigRepository.findByConfigKey("APPEAL_FEE")).thenReturn(Optional.empty());

        // Act - should not throw
        BlockStatisticsResponse response = examStatisticsService.getBlockStatistics(examId, blockId);

        // Assert
        assertNotNull(response);
        var oop = response.getAiOopAnalysis();
        assertEquals(1L, oop.getOopViolatedCount()); // only blankRawReview has isOopViolated=true
        assertEquals(0L, oop.getHardCodeCount());    // null/blank raw → skipped
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
        when(aiReviewRepository.findAllByBlockId(blockId)).thenReturn(Collections.emptyList());

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
        when(aiReviewRepository.findAllByBlockId(blockId)).thenReturn(Collections.emptyList());
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
