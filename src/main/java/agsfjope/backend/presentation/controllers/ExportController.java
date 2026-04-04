package agsfjope.backend.presentation.controllers;

import agsfjope.backend.core.repositories.block.BlockRepository;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.infrastructure.excel.ExcelExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

/**
 * REST Controller cho chức năng xuất file Excel.
 *
 * <p>Cung cấp 2 endpoint download file Excel theo block:</p>
 * <pre>
 * GET /api/exams/{examId}/blocks/{blockId}/export/grade-sheet       — Bảng điểm
 * GET /api/exams/{examId}/blocks/{blockId}/export/statistics-report — Báo cáo thống kê
 * </pre>
 *
 * <p>Authorization: Chỉ {@code EXAM_STAFF} và {@code SYSTEM_ADMIN} mới được truy cập.</p>
 *
 * <p>Response: File `.xlsx` trả về qua {@code Content-Disposition: attachment}.</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ExportController {

    private final ExcelExportService excelExportService;
    private final BlockRepository    blockRepository;

    private static final String STAFF_ROLES =
            "hasAnyAuthority('EXAM_STAFF','ROLE_EXAM_STAFF','SYSTEM_ADMIN','ROLE_SYSTEM_ADMIN')";

    private static final String EXCEL_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    // ─── API 1: Xuất Bảng Điểm ─────────────────────────────────────────────────

    /**
     * Xuất file Excel bảng điểm tổng hợp cho một block.
     *
     * <p>File trả về gồm 1 sheet: STT, Họ tên, MSSV, Email,
     * Điểm TC từng câu hỏi (dynamic), Tổng TC, Điểm OOP, Tổng điểm, Kết quả (Pass/Fail).</p>
     *
     * @param examId  UUID của kỳ thi
     * @param blockId UUID của block
     * @return file .xlsx bảng điểm
     */
    @GetMapping("/api/exams/{examId}/blocks/{blockId}/export/grade-sheet")
    @PreAuthorize(STAFF_ROLES)
    public ResponseEntity<byte[]> exportGradeSheet(
            @PathVariable UUID examId,
            @PathVariable UUID blockId
    ) {
        // Dùng findByBlockIdWithExam để eager-load Exam trong 1 query (tránh LazyInitializationException)
        var block = blockRepository.findByBlockIdWithExam(blockId)
                .orElseThrow(() -> new NotFoundException("Block không tồn tại."));

        // Lấy Semester từ Block → Exam
        String semester  = block.getExam() != null ? block.getExam().getSemester() : "Unknown";
        String blockName = block.getName() != null ? block.getName() : blockId.toString();

        log.info("[Export] Grade Sheet request: blockId={} blockName='{}' semester='{}'",
                blockId, blockName, semester);

        try {
            byte[] fileBytes = excelExportService.generateGradeSheet(blockId, semester, blockName);
            String fileName  = excelExportService.gradeSheetFileName(semester, blockName);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"")
                    .body(fileBytes);

        } catch (IOException e) {
            log.error("[Export] Failed to generate grade sheet for blockId={}: {}", blockId, e.getMessage(), e);
            throw new RuntimeException("Không thể tạo file Excel bảng điểm. Vui lòng thử lại.", e);
        }
    }

    // ─── API 2: Xuất Báo Cáo Thống Kê ─────────────────────────────────────────

    /**
     * Xuất file Excel báo cáo thống kê cho một block.
     *
     * <p>File trả về gồm 4 sheet, mỗi sheet có bảng dữ liệu và biểu đồ minh họa:</p>
     * <ol>
     *   <li>Tổng Quan: bài nộp / đã chấm + Pie chart</li>
     *   <li>Phân Tích Điểm: avg/max/min, pass/fail, phân phối điểm + Bar chart</li>
     *   <li>Phân Tích AI OOP: vi phạm từng tiêu chí OOP + Bar chart</li>
     *   <li>Phúc Khảo & Tài Chính: breakdown trạng thái, doanh thu + Pie chart</li>
     * </ol>
     *
     * @param examId  UUID của kỳ thi (dùng để validate block thuộc đúng exam)
     * @param blockId UUID của block
     * @return file .xlsx báo cáo thống kê
     */
    @GetMapping("/api/exams/{examId}/blocks/{blockId}/export/statistics-report")
    @PreAuthorize(STAFF_ROLES)
    public ResponseEntity<byte[]> exportStatisticsReport(
            @PathVariable UUID examId,
            @PathVariable UUID blockId
    ) {
        // Dùng findByBlockIdWithExam để eager-load Exam trong 1 query (tránh LazyInitializationException)
        var block = blockRepository.findByBlockIdWithExam(blockId)
                .orElseThrow(() -> new NotFoundException("Block không tồn tại."));

        String semester  = block.getExam() != null ? block.getExam().getSemester() : "Unknown";
        String blockName = block.getName() != null ? block.getName() : blockId.toString();

        log.info("[Export] Statistics Report request: examId={} blockId={} blockName='{}' semester='{}'",
                examId, blockId, blockName, semester);

        try {
            byte[] fileBytes = excelExportService.generateStatisticsReport(examId, blockId, semester, blockName);
            String fileName  = excelExportService.statisticsReportFileName(semester, blockName);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"")
                    .body(fileBytes);

        } catch (IOException e) {
            log.error("[Export] Failed to generate statistics report for blockId={}: {}", blockId, e.getMessage(), e);
            throw new RuntimeException("Không thể tạo file Excel báo cáo thống kê. Vui lòng thử lại.", e);
        }
    }
}
