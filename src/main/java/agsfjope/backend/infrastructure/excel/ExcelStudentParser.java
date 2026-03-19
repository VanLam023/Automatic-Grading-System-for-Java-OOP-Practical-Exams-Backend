package agsfjope.backend.infrastructure.excel;

import agsfjope.backend.application.dtos.requests.user.ImportStudentRequest;
import agsfjope.backend.application.dtos.responses.user.ImportStudentResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Infrastructure component responsible for parsing an Excel (.xlsx) file
 * and converting each row into an ImportStudentRequest object.
 *
 * <p>Expected file format (no specific column name required, order matters):
 * <ul>
 *   <li>Column A (index 0): Email (e.g. duyntse170601@fpt.edu.vn)</li>
 *   <li>Column B (index 1): FullName (e.g. Nguyễn Thái Duy)</li>
 *   <li>Column C (index 2): MSSV (e.g. SE170601)</li>
 * </ul>
 * Row 1 is treated as the header and is skipped automatically.</p>
 */
@Slf4j
@Component
public class ExcelStudentParser {

    // Column index constants for readability
    private static final int COL_EMAIL    = 0;
    private static final int COL_FULLNAME = 1;
    private static final int COL_MSSV     = 2;

    /**
     * Parses the uploaded .xlsx file and returns a list of valid parsed rows
     * along with a list of skipped rows for invalid/empty entries.
     *
     * <p>Rows are skipped (and added to skippedDetails) when:
     * <ul>
     *   <li>The row is completely blank</li>
     *   <li>Email, FullName, or MSSV cell is empty</li>
     *   <li>Email does not end with {@code @fpt.edu.vn}</li>
     * </ul>
     * </p>
     *
     * @param file     the uploaded multipart Excel file
     * @param skipped  a mutable list; this method appends SkippedRow entries for invalid rows
     * @return list of successfully parsed student rows (may be empty)
     * @throws RuntimeException if file cannot be read or is not a valid .xlsx file
     */
    public List<ImportStudentRequest> parse(MultipartFile file,
                                            List<ImportStudentResponse.SkippedRow> skipped) {
        List<ImportStudentRequest> result = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            // Always read the first sheet regardless of its name
            Sheet sheet = workbook.getSheetAt(0);

            // Iterate from row index 1 (skip header at index 0)
            // getLastRowNum() returns the 0-based index of the last row
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                // Skip completely blank rows that Excel sometimes generates
                if (row == null || isRowEmpty(row)) {
                    log.debug("[ExcelParser] Row {} is empty, skipping.", i + 1);
                    continue;
                }

                // Extract cell values as trimmed strings
                String email    = getCellValue(row, COL_EMAIL).trim();
                String fullName = getCellValue(row, COL_FULLNAME).trim();
                String mssv     = getCellValue(row, COL_MSSV).trim();
                int displayRow  = i + 1; // user-facing row number (1-based, header = row 1)

                // Validate that required fields are not empty
                if (email.isEmpty() || fullName.isEmpty() || mssv.isEmpty()) {
                    log.warn("[ExcelParser] Row {} has missing fields. Skipping.", displayRow);
                    skipped.add(ImportStudentResponse.SkippedRow.builder()
                            .rowNumber(displayRow)
                            .email(email.isEmpty() ? "(empty)" : email)
                            .reason("Thiếu dữ liệu: Email, FullName hoặc MSSV bị trống")
                            .build());
                    continue;
                }

                // Validate FPT email format
                if (!email.toLowerCase().endsWith("@fpt.edu.vn")) {
                    log.warn("[ExcelParser] Row {} has invalid email: {}. Skipping.", displayRow, email);
                    skipped.add(ImportStudentResponse.SkippedRow.builder()
                            .rowNumber(displayRow)
                            .email(email)
                            .reason("Email không đúng định dạng @fpt.edu.vn")
                            .build());
                    continue;
                }

                // Row is valid — add to result list
                result.add(ImportStudentRequest.builder()
                        .email(email)
                        .fullName(fullName)
                        .mssv(mssv.toUpperCase()) // normalize MSSV to uppercase (SE170601)
                        .build());
            }

        } catch (IOException e) {
            log.error("[ExcelParser] Failed to read Excel file: {}", e.getMessage());
            throw new RuntimeException("Không thể đọc file Excel. Vui lòng kiểm tra lại định dạng .xlsx", e);
        }

        log.info("[ExcelParser] Parsed {} valid rows, {} skipped.", result.size(), skipped.size());
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reads a cell's value as a String regardless of its actual cell type.
     * Numeric cells (e.g. a MSSV like "170601" read as a number) are converted
     * using DataFormatter to preserve the original display value.
     *
     * @param row       the Excel row
     * @param colIndex  the 0-based column index
     * @return string value of the cell, or empty string if cell is null/blank
     */
    private String getCellValue(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";

        // DataFormatter renders the cell exactly as Excel displays it,
        // handling numbers, dates, strings uniformly
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell);
    }

    /**
     * Checks whether a row has no meaningful content across its first 3 columns.
     * Used to skip ghost rows that Excel sometimes writes when pasting data.
     *
     * @param row the row to inspect
     * @return true if all cells in columns A–C are blank
     */
    private boolean isRowEmpty(Row row) {
        for (int col = 0; col < 3; col++) {
            Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
}
