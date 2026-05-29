package agsfjope.backend.infrastructure.excel;

import agsfjope.backend.application.dtos.responses.grading.AnswerGradingDetail;
import agsfjope.backend.application.dtos.responses.grading.GradingResultResponse;
import agsfjope.backend.application.dtos.responses.statistics.BlockStatisticsResponse;
import agsfjope.backend.application.dtos.responses.statistics.BlockStatisticsResponse.*;
import agsfjope.backend.application.examstatisticsservices.ExamStatisticsService;
import agsfjope.backend.application.gradingservices.GradingQueryService;
import agsfjope.backend.core.enums.GradingResultStatus;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelExportServiceTest {

    @Mock
    private GradingQueryService gradingQueryService;

    @Mock
    private ExamStatisticsService examStatisticsService;

    @InjectMocks
    private ExcelExportService excelExportService;

    @Test
    @DisplayName("[N] generateGradeSheet - generates valid workbook bytes with expected sheets and custom data")
    void generateGradeSheet_ValidData_GeneratesSuccessfully() throws IOException {
        // Arrange
        UUID blockId = UUID.randomUUID();
        String semester = "SU2025";
        String blockName = "Block 1";

        GradingResultResponse result = GradingResultResponse.builder()
                .studentName("Nguyen Van A")
                .studentCode("HE150000")
                .studentEmail("anv@fpt.edu.vn")
                .status(GradingResultStatus.PASS)
                .testCaseScore(new BigDecimal("3.50"))
                .oopScore(new BigDecimal("5.00"))
                .totalScore(new BigDecimal("8.50"))
                .answers(List.of(AnswerGradingDetail.builder()
                        .questionNumber(1)
                        .rawTestCaseScore(new BigDecimal("3.50"))
                        .build()))
                .build();

        when(gradingQueryService.getBlockResultsWithDetails(blockId)).thenReturn(new ArrayList<>(List.of(result)));

        // Act
        byte[] bytes = excelExportService.generateGradeSheet(blockId, semester, blockName);

        // Assert
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertNotNull(workbook.getSheet("Bảng Điểm"));
            var sheet = workbook.getSheet("Bảng Điểm");
            assertTrue(sheet.getLastRowNum() >= 2); // Title, Header, Data rows
        }
    }

    @Test
    @DisplayName("[N] generateStatisticsReport - generates statistics workbook with 5 sheets including the new testcase sheet")
    void generateStatisticsReport_ValidStats_GeneratesAllSheets() throws IOException {
        // Arrange
        UUID examId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        String semester = "SU2025";
        String blockName = "Block 1";

        ScoreAnalysis scoreAnalysis = ScoreAnalysis.builder()
                .avgScore(new BigDecimal("7.20"))
                .maxScore(new BigDecimal("9.50"))
                .minScore(new BigDecimal("4.00"))
                .passCount(10L)
                .failCount(2L)
                .passRate(83.3)
                .failRate(16.7)
                .distribution(List.of(ScoreBucket.builder().range("7-8").count(5L).percentage(41.7).build()))
                .build();

        AiOopAnalysis aiOopAnalysis = AiOopAnalysis.builder()
                .avgOopScore(new BigDecimal("8.00"))
                .oopViolatedCount(1L)
                .oopViolatedRate(8.3)
                .hardCodeCount(0L)
                .hardCodeRate(0.0)
                .encapsulationViolations(0L)
                .inheritanceViolations(1L)
                .polymorphismViolations(0L)
                .designQualityViolations(0L)
                .codeIntegrityViolations(0L)
                .build();

        AppealFinancialAnalysis appealFinancial = AppealFinancialAnalysis.builder()
                .totalAppeals(2L)
                .pendingCount(0L)
                .processingCount(1L)
                .approvedCount(1L)
                .deniedCount(0L)
                .approvedRate(50.0)
                .deniedRate(0.0)
                .totalFeesCollected(new BigDecimal("400000"))
                .totalRefunded(new BigDecimal("200000"))
                .netRevenue(new BigDecimal("200000"))
                .build();

        TestCaseStat tcStat = TestCaseStat.builder()
                .name("Câu 1 - Test Case 1")
                .avgScore(new BigDecimal("1.00"))
                .failureCount(1L)
                .failureRate(8.3)
                .sampleSize(12L)
                .build();

        BlockStatisticsResponse stats = BlockStatisticsResponse.builder()
                .totalSubmissions(15L)
                .gradedSubmissions(12L)
                .scoreAnalysis(scoreAnalysis)
                .aiOopAnalysis(aiOopAnalysis)
                .appealFinancial(appealFinancial)
                .testCaseStats(List.of(tcStat))
                .build();

        when(examStatisticsService.getBlockStatistics(examId, blockId)).thenReturn(stats);

        // Act
        byte[] bytes = excelExportService.generateStatisticsReport(examId, blockId, semester, blockName);

        // Assert
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertNotNull(workbook.getSheet("Tổng Quan"));
            assertNotNull(workbook.getSheet("Phân Tích Điểm"));
            assertNotNull(workbook.getSheet("Phân Tích AI OOP"));
            assertNotNull(workbook.getSheet("Phúc Khảo & Tài Chính"));
            assertNotNull(workbook.getSheet("Chi Tiết Test Case"));

            var tcSheet = workbook.getSheet("Chi Tiết Test Case");
            assertTrue(tcSheet.getLastRowNum() >= 2);
        }
    }

    @Test
    @DisplayName("[N] gradeSheetFileName / statisticsReportFileName - formats name with timestamp correctly")
    void testFileNames_SanitizeAndFormat() {
        String semester = "SU2025";
        String blockName = "Block/1"; // Has a slash to check sanitize

        String gradeFileName = excelExportService.gradeSheetFileName(semester, blockName);
        assertNotNull(gradeFileName);
        assertTrue(gradeFileName.startsWith("BangDiem_SU2025_Block_1_"));
        assertTrue(gradeFileName.endsWith(".xlsx"));

        String statsFileName = excelExportService.statisticsReportFileName(semester, blockName);
        assertNotNull(statsFileName);
        assertTrue(statsFileName.startsWith("ThongKe_SU2025_Block_1_"));
        assertTrue(statsFileName.endsWith(".xlsx"));
    }
}
