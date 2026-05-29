package agsfjope.backend.infrastructure.excel;

import agsfjope.backend.application.dtos.responses.grading.AnswerGradingDetail;
import agsfjope.backend.application.dtos.responses.grading.GradingResultResponse;
import agsfjope.backend.application.dtos.responses.statistics.BlockStatisticsResponse;
import agsfjope.backend.application.dtos.responses.statistics.BlockStatisticsResponse.ScoreBucket;
import agsfjope.backend.application.dtos.responses.statistics.BlockStatisticsResponse.TestCaseStat;
import agsfjope.backend.application.examstatisticsservices.ExamStatisticsService;
import agsfjope.backend.application.gradingservices.GradingQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Infrastructure service responsible for generating Excel (.xlsx) reports.
 *
 * <p>Provides two primary exports:</p>
 * <ol>
 *   <li>{@link #generateGradeSheet(UUID, String, String)} — Bảng điểm tổng hợp toàn block
 *       gồm điểm từng câu hỏi (dynamic columns) + Tổng TC, OOP, Tổng điểm, Kết quả.</li>
 *   <li>{@link #generateStatisticsReport(UUID, UUID, String, String)} — Báo cáo thống kê
 *       4 sheet với bảng dữ liệu + biểu đồ (Pie / Bar chart).</li>
 * </ol>
 *
 * <p>Requires Apache POI (XSSFWorkbook, XDDF chart API) — already in pom.xml.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private final GradingQueryService     gradingQueryService;
    private final ExamStatisticsService   examStatisticsService;

    // ─── Timestamp format for file name: yyyyMMdd_HHmmss ───────────────────────
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // ─── Column widths (in 256ths of a character) ───────────────────────────────
    private static final int WIDTH_STT      = 8 * 256;
    private static final int WIDTH_NAME     = 28 * 256;
    private static final int WIDTH_MSSV     = 14 * 256;
    private static final int WIDTH_EMAIL    = 36 * 256;
    private static final int WIDTH_SCORE    = 14 * 256;
    private static final int WIDTH_RESULT   = 14 * 256;
    private static final int WIDTH_LABEL    = 32 * 256;
    private static final int WIDTH_VALUE    = 20 * 256;

    // ─── Color constants (XSSF hex) ─────────────────────────────────────────────
    private static final byte[] COLOR_HEADER      = hexDecoded("2153A4"); // deep blue
    private static final byte[] COLOR_Q_HEADER    = hexDecoded("3A6FC9"); // lighter blue (q columns)
    private static final byte[] COLOR_PASS        = hexDecoded("D6F0D5"); // light green
    private static final byte[] COLOR_FAIL        = hexDecoded("FFD6D8"); // light red
    private static final byte[] COLOR_SHEET_TITLE = hexDecoded("1A3A6B"); // navy text

    // ─────────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Generates the grade-sheet Excel file for a specific block.
     *
     * <p>Columns: STT | Họ tên | MSSV | Email | Q1(TC) | Q2(TC) | ... | Qn(TC)
     *            | Tổng TC | Điểm OOP | Tổng điểm | Kết quả</p>
     *
     * @param blockId    the block UUID
     * @param semester   semester code (e.g. "FA2025") — used in file name
     * @param blockName  block display name — used in file name
     * @return byte array of the .xlsx file
     * @throws IOException if workbook serialization fails
     */
    public byte[] generateGradeSheet(UUID blockId, String semester, String blockName)
            throws IOException {

        // Lấy toàn bộ kết quả chấm kèm chi tiết từng câu hỏi
        List<GradingResultResponse> results = gradingQueryService.getBlockResultsWithDetails(blockId);
        log.info("[Excel] generateGradeSheet: blockId={}, {} results", blockId, results.size());

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Bảng Điểm");
            sheet.setDisplayGridlines(true);

            // ── Xác định số câu hỏi tối đa (để tạo cột động) ──────────────────
            int maxQuestions = results.stream()
                    .mapToInt(r -> r.getAnswers() != null ? r.getAnswers().size() : 0)
                    .max().orElse(0);

            // ── Tạo style ───────────────────────────────────────────────────────
            CellStyle headerStyle    = createHeaderStyle(workbook, COLOR_HEADER);
            CellStyle qHeaderStyle   = createHeaderStyle(workbook, COLOR_Q_HEADER);
            CellStyle passStyle      = createDataStyle(workbook, COLOR_PASS);
            CellStyle failStyle      = createDataStyle(workbook, COLOR_FAIL);
            CellStyle normalStyle    = createDataStyle(workbook, null);
            CellStyle scoreStyle     = createScoreStyle(workbook, null);
            CellStyle passScoreStyle = createScoreStyle(workbook, COLOR_PASS);
            CellStyle failScoreStyle = createScoreStyle(workbook, COLOR_FAIL);

            // ── Tiêu đề sheet (row 0) ───────────────────────────────────────────
            Row titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(24);
            int totalCols = 4 + maxQuestions + 4; // STT,Name,MSSV,Email + Qx + TotalTC,OOP,Total,Result
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BẢNG ĐIỂM KỲ THI — " + blockName + " [" + semester + "]");
            titleCell.setCellStyle(createTitleStyle(workbook));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, Math.max(totalCols - 1, 1)));

            // ── Header row (row 1) ──────────────────────────────────────────────
            Row header = sheet.createRow(1);
            header.setHeightInPoints(20);
            int col = 0;
            createStyledCell(header, col++, "STT",        headerStyle);
            createStyledCell(header, col++, "Họ và Tên",  headerStyle);
            createStyledCell(header, col++, "MSSV",       headerStyle);
            createStyledCell(header, col++, "Email",      headerStyle);
            // Cột động: Q1, Q2, ..., Qn (điểm TC từng câu hỏi)
            for (int q = 1; q <= maxQuestions; q++) {
                createStyledCell(header, col++, "Câu " + q + " (TC)", qHeaderStyle);
            }
            createStyledCell(header, col++, "Tổng TC",    headerStyle);
            createStyledCell(header, col++, "Điểm OOP",   headerStyle);
            createStyledCell(header, col++, "Tổng Điểm",  headerStyle);
            createStyledCell(header, col,   "Kết Quả",    headerStyle);

            // ── Data rows ───────────────────────────────────────────────────────
            // Sắp xếp theo MSSV để dễ tra cứu
            results.sort(Comparator.comparing(
                    r -> r.getStudentCode() != null ? r.getStudentCode() : "",
                    String.CASE_INSENSITIVE_ORDER));

            for (int i = 0; i < results.size(); i++) {
                GradingResultResponse r = results.get(i);
                boolean isPassed = r.getStatus() != null &&
                                   r.getStatus().name().equalsIgnoreCase("PASS");

                // Chọn style row dựa theo Pass/Fail
                CellStyle rowStyle    = isPassed ? passStyle     : failStyle;
                CellStyle rowScoreStyle = isPassed ? passScoreStyle : failScoreStyle;

                Row row = sheet.createRow(i + 2); // +2 vì row 0 = title, row 1 = header
                row.setHeightInPoints(16);

                int c = 0;
                createStyledCell(row, c++, String.valueOf(i + 1), rowStyle);
                createStyledCell(row, c++, nullSafe(r.getStudentName()),  rowStyle);
                createStyledCell(row, c++, nullSafe(r.getStudentCode()),  rowStyle);
                createStyledCell(row, c++, nullSafe(r.getStudentEmail()), rowStyle);

                // Điền điểm từng câu hỏi vào cột Q1..Qn
                // Dùng Map<questionNumber, rawTestCaseScore> để tránh sai thứ tự
                Map<Integer, BigDecimal> qScores = new TreeMap<>();
                if (r.getAnswers() != null) {
                    for (AnswerGradingDetail ans : r.getAnswers()) {
                        qScores.put(ans.getQuestionNumber(),
                                ans.getRawTestCaseScore() != null
                                        ? ans.getRawTestCaseScore()
                                        : BigDecimal.ZERO);
                    }
                }
                for (int q = 1; q <= maxQuestions; q++) {
                    BigDecimal qScore = qScores.getOrDefault(q, BigDecimal.ZERO);
                    Cell qCell = row.createCell(c++);
                    qCell.setCellValue(qScore.doubleValue());
                    qCell.setCellStyle(rowScoreStyle);
                }

                // Tổng TC / OOP / Tổng điểm / Kết quả
                createNumericCell(row, c++, r.getTestCaseScore(), rowScoreStyle);
                createNumericCell(row, c++, r.getOopScore(),      rowScoreStyle);
                createNumericCell(row, c++, r.getTotalScore(),    rowScoreStyle);
                createStyledCell(row, c, isPassed ? "PASS ✓" : "FAIL ✗", rowStyle);
            }

            // ── Đặt độ rộng cột ────────────────────────────────────────────────
            int cIdx = 0;
            sheet.setColumnWidth(cIdx++, WIDTH_STT);
            sheet.setColumnWidth(cIdx++, WIDTH_NAME);
            sheet.setColumnWidth(cIdx++, WIDTH_MSSV);
            sheet.setColumnWidth(cIdx++, WIDTH_EMAIL);
            for (int q = 0; q < maxQuestions; q++) {
                sheet.setColumnWidth(cIdx++, WIDTH_SCORE);
            }
            sheet.setColumnWidth(cIdx++, WIDTH_SCORE);
            sheet.setColumnWidth(cIdx++, WIDTH_SCORE);
            sheet.setColumnWidth(cIdx++, WIDTH_SCORE);
            sheet.setColumnWidth(cIdx,   WIDTH_RESULT);

            // ── Freeze panes: cố định 2 dòng đầu (title + header) ──────────────
            sheet.createFreezePane(0, 2);

            return toBytes(workbook);
        }
    }

    /**
     * Generates the statistics-report Excel file for a specific block.
     *
     * <p>4 sheets: (1) Tổng Quan + Pie, (2) Phân Tích Điểm + Bar,
     * (3) AI OOP + Bar, (4) Phúc Khảo & Tài Chính + Pie.</p>
     *
     * @param examId    exam UUID (used to call ExamStatisticsService)
     * @param blockId   block UUID
     * @param semester  semester code — used in file name
     * @param blockName block display name — used in file name
     * @return byte array of the .xlsx file
     * @throws IOException if workbook serialization fails
     */
    public byte[] generateStatisticsReport(UUID examId, UUID blockId,
                                           String semester, String blockName)
            throws IOException {

        BlockStatisticsResponse stats = examStatisticsService.getBlockStatistics(examId, blockId);
        log.info("[Excel] generateStatisticsReport: blockId={}", blockId);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            // ── Sheet 1: Tổng Quan + Pie Chart ──────────────────────────────────
            buildOverviewSheet(workbook, stats, blockName, semester);

            // ── Sheet 2: Phân Tích Điểm + Bar Chart ─────────────────────────────
            buildScoreAnalysisSheet(workbook, stats, blockName, semester);

            // ── Sheet 3: Phân Tích AI OOP + Bar Chart ───────────────────────────
            buildAiOopSheet(workbook, stats, blockName, semester);

            // ── Sheet 4: Phúc Khảo & Tài Chính + Pie Chart ──────────────────────
            buildAppealFinancialSheet(workbook, stats, blockName, semester);

            // ── Sheet 5: Chi Tiết Test Case ──────────────────────────────────────
            buildTestCaseStatsSheet(workbook, stats, blockName, semester);

            return toBytes(workbook);
        }
    }

    /**
     * Returns the suggested file name for the grade-sheet download.
     * Format: {@code BangDiem_{Semester}_{BlockName}_{timestamp}.xlsx}
     */
    public String gradeSheetFileName(String semester, String blockName) {
        String ts = LocalDateTime.now().format(TIMESTAMP_FMT);
        return sanitize("BangDiem_" + semester + "_" + blockName + "_" + ts + ".xlsx");
    }

    /**
     * Returns the suggested file name for the statistics-report download.
     * Format: {@code ThongKe_{Semester}_{BlockName}_{timestamp}.xlsx}
     */
    public String statisticsReportFileName(String semester, String blockName) {
        String ts = LocalDateTime.now().format(TIMESTAMP_FMT);
        return sanitize("ThongKe_" + semester + "_" + blockName + "_" + ts + ".xlsx");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // SHEET BUILDERS
    // ─────────────────────────────────────────────────────────────────────────────

    /** Sheet 1: Tổng quan (Submitted vs Graded) + Pie chart */
    private void buildOverviewSheet(XSSFWorkbook wb, BlockStatisticsResponse stats,
                                    String blockName, String semester) {
        XSSFSheet sheet = wb.createSheet("Tổng Quan");
        sheet.setDisplayGridlines(true);

        CellStyle labelStyle  = createLabelStyle(wb);
        CellStyle valueStyle  = createValueStyle(wb);
        CellStyle titleStyle  = createTitleStyle(wb);

        // Tiêu đề
        addSheetTitle(sheet, "Tổng Quan Bài Nộp — " + blockName + " [" + semester + "]", titleStyle, 2);

        // Dữ liệu
        int row = 2;
        row = addLabelValue(sheet, row, "Tổng bài nộp",   String.valueOf(stats.getTotalSubmissions()),  labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Đã chấm xong",    String.valueOf(stats.getGradedSubmissions()), labelStyle, valueStyle);
        long notGraded = Math.max(0, stats.getTotalSubmissions() - stats.getGradedSubmissions());
        addLabelValue(sheet, row, "Chưa chấm", String.valueOf(notGraded), labelStyle, valueStyle);

        sheet.setColumnWidth(0, WIDTH_LABEL);
        sheet.setColumnWidth(1, WIDTH_VALUE);

        // Pie chart: Đã chấm / Chưa chấm
        addPieChart(sheet, wb,
                new String[]{"Đã chấm", "Chưa chấm"},
                new long[]{stats.getGradedSubmissions(), notGraded},
                "Tỷ Lệ Bài Đã Chấm",
                6, 0, 22, 14);
    }

    /** Sheet 2: Phân tích điểm (avg/max/min, pass/fail, distribution) + Bar chart */
    private void buildScoreAnalysisSheet(XSSFWorkbook wb, BlockStatisticsResponse stats,
                                          String blockName, String semester) {
        XSSFSheet sheet = wb.createSheet("Phân Tích Điểm");
        sheet.setDisplayGridlines(true);
        var sa = stats.getScoreAnalysis();

        CellStyle labelStyle = createLabelStyle(wb);
        CellStyle valueStyle = createValueStyle(wb);
        CellStyle titleStyle = createTitleStyle(wb);
        CellStyle hdrStyle   = createHeaderStyle(wb, COLOR_HEADER);

        addSheetTitle(sheet, "Phân Tích Điểm — " + blockName + " [" + semester + "]", titleStyle, 2);

        int row = 2;
        row = addLabelValue(sheet, row, "Điểm trung bình", fmt(sa.getAvgScore()), labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Điểm cao nhất",   fmt(sa.getMaxScore()), labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Điểm thấp nhất",  fmt(sa.getMinScore()), labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Số lượng PASS",   String.valueOf(sa.getPassCount()), labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Tỷ lệ PASS",      pct(sa.getPassRate()), labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Số lượng FAIL",   String.valueOf(sa.getFailCount()), labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Tỷ lệ FAIL",      pct(sa.getFailRate()), labelStyle, valueStyle);

        // Bảng phân phối điểm
        row++;
        Row distHeader = sheet.createRow(row++);
        createStyledCell(distHeader, 0, "Khoảng điểm", hdrStyle);
        createStyledCell(distHeader, 1, "Số lượng",    hdrStyle);
        createStyledCell(distHeader, 2, "Tỷ lệ %",     hdrStyle);

        // Lưu vị trí bắt đầu của dữ liệu biểu đồ để reference sau
        int distDataStart = row;
        if (sa.getDistribution() != null) {
            for (ScoreBucket bucket : sa.getDistribution()) {
                Row r = sheet.createRow(row++);
                createStyledCell(r, 0, bucket.getRange() + " điểm", createDataStyle(wb, null));
                Cell cntCell = r.createCell(1);
                cntCell.setCellValue(bucket.getCount());
                cntCell.setCellStyle(createDataStyle(wb, null));
                Cell pctCell = r.createCell(2);
                pctCell.setCellValue(bucket.getPercentage());
                pctCell.setCellStyle(createDataStyle(wb, null));
            }
        }
        int distDataEnd = row - 1;

        sheet.setColumnWidth(0, WIDTH_LABEL);
        sheet.setColumnWidth(1, WIDTH_VALUE);
        sheet.setColumnWidth(2, WIDTH_VALUE);

        // Bar chart: phân phối điểm
        if (sa.getDistribution() != null && !sa.getDistribution().isEmpty()) {
            addBarChart(sheet, wb,
                    sa.getDistribution().stream().map(ScoreBucket::getRange).toList(),
                    sa.getDistribution().stream().mapToLong(ScoreBucket::getCount).toArray(),
                    "Phân Phối Điểm",
                    distDataStart + 2, 0, distDataStart + 20, 14);
        }
    }

    /** Sheet 3: Phân tích AI OOP (criteria violations) + Bar chart */
    private void buildAiOopSheet(XSSFWorkbook wb, BlockStatisticsResponse stats,
                                   String blockName, String semester) {
        XSSFSheet sheet = wb.createSheet("Phân Tích AI OOP");
        sheet.setDisplayGridlines(true);
        var ai = stats.getAiOopAnalysis();

        CellStyle labelStyle = createLabelStyle(wb);
        CellStyle valueStyle = createValueStyle(wb);
        CellStyle titleStyle = createTitleStyle(wb);
        CellStyle hdrStyle   = createHeaderStyle(wb, COLOR_HEADER);

        addSheetTitle(sheet, "Phân Tích AI OOP — " + blockName + " [" + semester + "]", titleStyle, 2);

        int row = 2;
        row = addLabelValue(sheet, row, "Điểm OOP trung bình",   fmt(ai.getAvgOopScore()),  labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Vi phạm OOP (count)",    String.valueOf(ai.getOopViolatedCount()), labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Vi phạm OOP (%)",        pct(ai.getOopViolatedRate()), labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Hard-code (count)",      String.valueOf(ai.getHardCodeCount()), labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Hard-code (%)",          pct(ai.getHardCodeRate()), labelStyle, valueStyle);

        // Bảng vi phạm từng tiêu chí
        row++;
        Row hdr = sheet.createRow(row++);
        createStyledCell(hdr, 0, "Tiêu chí",      hdrStyle);
        createStyledCell(hdr, 1, "Số vi phạm",    hdrStyle);
        createStyledCell(hdr, 2, "Tỷ lệ vi phạm", hdrStyle);

        String[] criteria = {"Encapsulation", "Inheritance", "Polymorphism", "Design Quality", "Code Integrity"};
        long[]   counts   = {
            ai.getEncapsulationViolations(), ai.getInheritanceViolations(),
            ai.getPolymorphismViolations(),  ai.getDesignQualityViolations(),
            ai.getCodeIntegrityViolations()
        };
        double[] rates = {
            ai.getEncapsulationViolationRate(), ai.getInheritanceViolationRate(),
            ai.getPolymorphismViolationRate(),  ai.getDesignQualityViolationRate(),
            ai.getCodeIntegrityViolationRate()
        };

        int criteriaStart = row;
        for (int i = 0; i < criteria.length; i++) {
            Row r = sheet.createRow(row++);
            createStyledCell(r, 0, criteria[i], createDataStyle(wb, null));
            Cell cCell = r.createCell(1);
            cCell.setCellValue(counts[i]);
            cCell.setCellStyle(createDataStyle(wb, null));
            Cell pCell = r.createCell(2);
            pCell.setCellValue(rates[i]);
            pCell.setCellStyle(createDataStyle(wb, null));
        }

        sheet.setColumnWidth(0, WIDTH_LABEL);
        sheet.setColumnWidth(1, WIDTH_VALUE);
        sheet.setColumnWidth(2, WIDTH_VALUE);

        // Bar chart: số vi phạm từng tiêu chí
        addBarChart(sheet, wb,
                List.of(criteria),
                counts,
                "Vi Phạm OOP Theo Tiêu Chí",
                criteriaStart + 6, 0, criteriaStart + 24, 14);
    }

    /** Sheet 4: Phúc khảo & tài chính + Pie chart */
    private void buildAppealFinancialSheet(XSSFWorkbook wb, BlockStatisticsResponse stats,
                                            String blockName, String semester) {
        XSSFSheet sheet = wb.createSheet("Phúc Khảo & Tài Chính");
        sheet.setDisplayGridlines(true);
        var af = stats.getAppealFinancial();

        CellStyle labelStyle = createLabelStyle(wb);
        CellStyle valueStyle = createValueStyle(wb);
        CellStyle titleStyle = createTitleStyle(wb);

        addSheetTitle(sheet, "Phúc Khảo & Tài Chính — " + blockName + " [" + semester + "]", titleStyle, 2);

        int row = 2;
        row = addLabelValue(sheet, row, "Tổng đơn phúc khảo",  String.valueOf(af.getTotalAppeals()),   labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Đang chờ (PENDING)",   String.valueOf(af.getPendingCount()),   labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Đang xử lý",           String.valueOf(af.getProcessingCount()), labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Đã duyệt (APPROVED)",  String.valueOf(af.getApprovedCount()),  labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Tỷ lệ duyệt",          pct(af.getApprovedRate()),              labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Bị từ chối (DENIED)",  String.valueOf(af.getDeniedCount()),    labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Tỷ lệ từ chối",        pct(af.getDeniedRate()),                labelStyle, valueStyle);
        row++;
        row = addLabelValue(sheet, row, "Tổng phí thu (VND)",   fmtVnd(af.getTotalFeesCollected()), labelStyle, valueStyle);
        row = addLabelValue(sheet, row, "Tổng hoàn tiền (VND)", fmtVnd(af.getTotalRefunded()),      labelStyle, valueStyle);
        addLabelValue(sheet, row, "Doanh thu thuần (VND)", fmtVnd(af.getNetRevenue()),     labelStyle, valueStyle);

        sheet.setColumnWidth(0, WIDTH_LABEL);
        sheet.setColumnWidth(1, WIDTH_VALUE);

        // Pie chart: trạng thái phúc khảo
        addPieChart(sheet, wb,
                new String[]{"Pending", "Processing", "Approved", "Denied"},
                new long[]{af.getPendingCount(), af.getProcessingCount(),
                            af.getApprovedCount(), af.getDeniedCount()},
                "Trạng Thái Phúc Khảo",
                14, 0, 30, 14);
    }

    /** Sheet 5: Chi Tiết Test Case + Bar Chart */
    private void buildTestCaseStatsSheet(XSSFWorkbook wb, BlockStatisticsResponse stats,
                                         String blockName, String semester) {
        XSSFSheet sheet = wb.createSheet("Chi Tiết Test Case");
        sheet.setDisplayGridlines(true);
        var tcStats = stats.getTestCaseStats();

        CellStyle labelStyle = createLabelStyle(wb);
        CellStyle valueStyle = createValueStyle(wb);
        CellStyle titleStyle = createTitleStyle(wb);
        CellStyle hdrStyle   = createHeaderStyle(wb, COLOR_HEADER);
        CellStyle scoreStyle = createScoreStyle(wb, null);
        CellStyle normalStyle = createDataStyle(wb, null);

        addSheetTitle(sheet, "Phân Tích Chi Tiết Lỗi & Hiệu Năng Test Case — " + blockName + " [" + semester + "]", titleStyle, 7);

        int row = 2;
        Row hdr = sheet.createRow(row++);
        hdr.setHeightInPoints(20);
        createStyledCell(hdr, 0, "STT",          hdrStyle);
        createStyledCell(hdr, 1, "Tên Test Case", hdrStyle);
        createStyledCell(hdr, 2, "Điểm TB",       hdrStyle);
        createStyledCell(hdr, 3, "Số lượt lỗi",   hdrStyle);
        createStyledCell(hdr, 4, "Tỷ lệ lỗi",     hdrStyle);
        createStyledCell(hdr, 5, "Tỷ lệ đạt",     hdrStyle);
        createStyledCell(hdr, 6, "Số lượt chạy",   hdrStyle);

        if (tcStats != null && !tcStats.isEmpty()) {
            // Sắp xếp tự động từ Test Case dễ nhất (tỷ lệ thành công cao nhất/lỗi thấp nhất) đến khó nhất
            List<TestCaseStat> sortedList = new ArrayList<>(tcStats);
            sortedList.sort(Comparator.comparingDouble(TestCaseStat::getFailureRate));

            for (int i = 0; i < sortedList.size(); i++) {
                TestCaseStat tc = sortedList.get(i);
                Row r = sheet.createRow(row++);
                r.setHeightInPoints(16);

                double failRate = tc.getFailureRate();
                double successRate = Math.max(0, 100.0 - failRate);

                createStyledCell(r, 0, String.valueOf(i + 1), normalStyle);
                createStyledCell(r, 1, nullSafe(tc.getName()), normalStyle);

                Cell avgCell = r.createCell(2);
                avgCell.setCellValue(tc.getAvgScore() != null ? tc.getAvgScore().doubleValue() : 0.0);
                avgCell.setCellStyle(scoreStyle);

                Cell failCountCell = r.createCell(3);
                failCountCell.setCellValue(tc.getFailureCount());
                failCountCell.setCellStyle(scoreStyle);

                createStyledCell(r, 4, pct(failRate), normalStyle);
                createStyledCell(r, 5, pct(successRate), normalStyle);

                Cell sampleCell = r.createCell(6);
                sampleCell.setCellValue(tc.getSampleSize());
                sampleCell.setCellStyle(scoreStyle);
            }

            // Đặt độ rộng cột
            sheet.setColumnWidth(0, WIDTH_STT);
            sheet.setColumnWidth(1, 32 * 256);
            sheet.setColumnWidth(2, WIDTH_SCORE);
            sheet.setColumnWidth(3, WIDTH_SCORE);
            sheet.setColumnWidth(4, WIDTH_SCORE);
            sheet.setColumnWidth(5, WIDTH_SCORE);
            sheet.setColumnWidth(6, WIDTH_SCORE);

            // Bar chart: Số lượt lỗi theo Test Case
            addBarChart(sheet, wb,
                    sortedList.stream().map(TestCaseStat::getName).toList(),
                    sortedList.stream().mapToLong(TestCaseStat::getFailureCount).toArray(),
                    "Số Lượt Lỗi Theo Test Case",
                    row + 3, 0, row + 22, 12);
        } else {
            Row r = sheet.createRow(row++);
            createStyledCell(r, 1, "Chưa có dữ liệu thống kê Test Case.", normalStyle);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CHART HELPERS
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Tạo Pie Chart nhúng trực tiếp vào sheet (dùng XDDF API của Apache POI).
     *
     * @param sheet         sheet chứa biểu đồ
     * @param wb            workbook (cần để tạo XSSFDrawing)
     * @param categories    nhãn từng phần (labels)
     * @param values        giá trị tương ứng
     * @param title         tiêu đề biểu đồ
     * @param row1..col2    vị trí anchor biểu đồ trong sheet (hàng/cột, 0-based)
     */
    private void addPieChart(XSSFSheet sheet, XSSFWorkbook wb,
                              String[] categories, long[] values,
                              String title,
                              int row1, int col1, int row2, int col2) {
        try {
            XSSFDrawing drawing  = sheet.createDrawingPatriarch();
            XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, col1, row1, col2, row2);
            XSSFChart chart = drawing.createChart(anchor);
            chart.setTitleText(title);
            chart.setTitleOverlay(false);

            XDDFChartLegend legend = chart.getOrAddLegend();
            legend.setPosition(LegendPosition.RIGHT);

            // Tạo data source từ array in-memory (không cần ref cell)
            XDDFDataSource<String> catSource = XDDFDataSourcesFactory.fromArray(categories);
            XDDFNumericalDataSource<Long>  valSource = XDDFDataSourcesFactory.fromArray(
                    java.util.Arrays.stream(values).boxed().toArray(Long[]::new));

            XDDFChartData data = chart.createData(ChartTypes.PIE, null, null);
            data.setVaryColors(true);
            XDDFChartData.Series series = data.addSeries(catSource, valSource);
            series.setTitle(title, null);
            chart.plot(data);

        } catch (Exception e) {
            // Biểu đồ không bắt buộc — nếu lỗi thì vẫn giữ bảng dữ liệu
            log.warn("[Excel] Failed to create pie chart '{}': {}", title, e.getMessage());
        }
    }

    /**
     * Tạo Bar Chart nhúng vào sheet.
     */
    private void addBarChart(XSSFSheet sheet, XSSFWorkbook wb,
                              List<String> categories, long[] values,
                              String title,
                              int row1, int col1, int row2, int col2) {
        try {
            XSSFDrawing drawing = sheet.createDrawingPatriarch();
            XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, col1, row1, col2, row2);
            XSSFChart chart = drawing.createChart(anchor);
            chart.setTitleText(title);
            chart.setTitleOverlay(false);

            XDDFChartLegend legend = chart.getOrAddLegend();
            legend.setPosition(LegendPosition.BOTTOM);

            XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
            XDDFValueAxis    leftAxis   = chart.createValueAxis(AxisPosition.LEFT);
            leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);

            XDDFDataSource<String>         catSource = XDDFDataSourcesFactory.fromArray(
                    categories.toArray(String[]::new));
            XDDFNumericalDataSource<Long>   valSource = XDDFDataSourcesFactory.fromArray(
                    java.util.Arrays.stream(values).boxed().toArray(Long[]::new));

            XDDFBarChartData data = (XDDFBarChartData) chart.createData(
                    ChartTypes.BAR, bottomAxis, leftAxis);
            data.setBarDirection(BarDirection.COL);

            XDDFBarChartData.Series series = (XDDFBarChartData.Series) data.addSeries(catSource, valSource);
            series.setTitle(title, null);
            series.setShowLeaderLines(false);
            chart.plot(data);

        } catch (Exception e) {
            log.warn("[Excel] Failed to create bar chart '{}': {}", title, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // STYLE FACTORY METHODS
    // ─────────────────────────────────────────────────────────────────────────────

    /** Tạo style cho tiêu đề sheet: font lớn, in đậm, màu navy */
    private CellStyle createTitleStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        font.setColor(new XSSFColor(COLOR_SHEET_TITLE, null));
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /** Tạo style cho header row: nền màu + chữ trắng, in đậm, canh giữa */
    private CellStyle createHeaderStyle(XSSFWorkbook wb, byte[] bgColor) {
        XSSFCellStyle style = wb.createCellStyle();
        if (bgColor != null) {
            style.setFillForegroundColor(new XSSFColor(bgColor, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(style);
        style.setWrapText(true);
        return style;
    }

    /** Tạo style cho ô nhãn (label): in đậm, không nền */
    private CellStyle createLabelStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        setBorder(style);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /** Tạo style cho ô giá trị (value) */
    private CellStyle createValueStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        setBorder(style);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /** Tạo style cho ô dữ liệu (data cell trong bảng điểm): optional background */
    private CellStyle createDataStyle(XSSFWorkbook wb, byte[] bgColor) {
        XSSFCellStyle style = wb.createCellStyle();
        if (bgColor != null) {
            style.setFillForegroundColor(new XSSFColor(bgColor, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        setBorder(style);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /** Style cho ô số điểm: canh giữa, format 2 số thập phân */
    private CellStyle createScoreStyle(XSSFWorkbook wb, byte[] bgColor) {
        XSSFCellStyle style = wb.createCellStyle();
        if (bgColor != null) {
            style.setFillForegroundColor(new XSSFColor(bgColor, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        setBorder(style);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        // Format số: 0.00
        DataFormat df = wb.createDataFormat();
        style.setDataFormat(df.getFormat("0.00"));
        return style;
    }

    private void setBorder(XSSFCellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CELL / ROW HELPERS
    // ─────────────────────────────────────────────────────────────────────────────

    private void addSheetTitle(XSSFSheet sheet, String title, CellStyle style, int totalCols) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(24);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(style);
        if (totalCols > 1) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, totalCols - 1));
        }
        // Blank row separator
        sheet.createRow(1);
    }

    private int addLabelValue(XSSFSheet sheet, int rowIndex,
                               String label, String value,
                               CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(16);
        createStyledCell(row, 0, label, labelStyle);
        createStyledCell(row, 1, value, valueStyle);
        return rowIndex + 1;
    }

    private void createStyledCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private void createNumericCell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value.doubleValue() : 0.0);
        cell.setCellStyle(style);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FORMAT HELPERS
    // ─────────────────────────────────────────────────────────────────────────────

    private String fmt(BigDecimal v)    { return v != null ? String.format("%.2f", v) : "0.00"; }
    private String pct(double v)        { return String.format("%.1f%%", v); }
    private String nullSafe(String s)   { return s != null ? s : ""; }
    private String fmtVnd(BigDecimal v) {
        if (v == null) return "0 VND";
        return String.format("%,.0f VND", v.doubleValue());
    }

    private byte[] toBytes(XSSFWorkbook wb) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    /**
     * Làm sạch tên file: thay thế ký tự đặc biệt không hợp lệ trong tên file Windows/Linux.
     */
    private String sanitize(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static byte[] hexDecoded(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
