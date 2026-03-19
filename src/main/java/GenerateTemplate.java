import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import java.io.*;

/**
 * Standalone script to generate the student import Excel template.
 * Run via: mvn exec:java -Dexec.mainClass=GenerateTemplate
 */
public class GenerateTemplate {

    public static void main(String[] args) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("students");

        // ── Styles ──────────────────────────────────────────────────────────
        // Header style: orange background, white bold text, centered
        CellStyle headerStyle = workbook.createCellStyle();
        XSSFFont headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)243, (byte)113, (byte)32}, null)); // #f37120
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorderThin(headerStyle);

        // Even row style: light gray
        CellStyle evenStyle = workbook.createCellStyle();
        XSSFFont dataFont = workbook.createFont();
        dataFont.setFontHeightInPoints((short) 11);
        evenStyle.setFont(dataFont);
        evenStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)240, (byte)240, (byte)240}, null));
        evenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        evenStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorderThin(evenStyle);

        // Odd row style: white
        CellStyle oddStyle = workbook.createCellStyle();
        oddStyle.setFont(dataFont);
        oddStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorderThin(oddStyle);

        // Note style: italic gray small font
        CellStyle noteStyle = workbook.createCellStyle();
        XSSFFont noteFont = workbook.createFont();
        noteFont.setItalic(true);
        noteFont.setFontHeightInPoints((short) 9);
        noteFont.setColor(new XSSFColor(new byte[]{(byte)120, (byte)120, (byte)120}, null));
        noteStyle.setFont(noteFont);

        // ── Header Row ───────────────────────────────────────────────────────
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(28);
        String[] columns = {"Email", "FullName", "MSSV"};
        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        // ── Sample Data ───────────────────────────────────────────────────────
        String[][] data = {
            {"duyntse170601@fpt.edu.vn",  "Nguyen Thai Duy",  "SE170601"},
            {"anhtpse180202@fpt.edu.vn",  "Tran Phuong Anh",  "SE180202"},
            {"minhltse190303@fpt.edu.vn", "Le Thanh Minh",    "SE190303"},
            {"hungvse200404@fpt.edu.vn",  "Vu Van Hung",      "SE200404"},
            {"lanhbse210505@fpt.edu.vn",  "Bui Nhu Lanh",     "SE210505"}
        };

        for (int r = 0; r < data.length; r++) {
            Row row = sheet.createRow(r + 1);
            row.setHeightInPoints(22);
            CellStyle style = (r % 2 == 0) ? evenStyle : oddStyle;
            for (int c = 0; c < 3; c++) {
                Cell cell = row.createCell(c);
                cell.setCellValue(data[r][c]);
                cell.setCellStyle(style);
            }
        }

        // ── Note Row ─────────────────────────────────────────────────────────
        int noteRowIdx = data.length + 2;
        Row noteRow = sheet.createRow(noteRowIdx);
        Cell noteCell = noteRow.createCell(0);
        noteCell.setCellValue(
            "* Luu y: Chi nhan file .xlsx | Email phai co duoi @fpt.edu.vn | " +
            "MSSV viet hoa (vi du: SE170601) | Dong dau tien la header, du lieu bat dau tu dong 2"
        );
        noteCell.setCellStyle(noteStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(noteRowIdx, noteRowIdx, 0, 2));

        // ── Column widths ─────────────────────────────────────────────────────
        sheet.setColumnWidth(0, 38 * 256); // Email
        sheet.setColumnWidth(1, 30 * 256); // FullName
        sheet.setColumnWidth(2, 15 * 256); // MSSV

        // ── Freeze header row ─────────────────────────────────────────────────
        sheet.createFreezePane(0, 1);

        // ── Save ──────────────────────────────────────────────────────────────
        String outputPath = "d:\\DOAN\\docs\\student_import_template.xlsx";
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            workbook.write(fos);
        }
        workbook.close();

        System.out.println("SUCCESS: " + outputPath);
    }

    private static void setBorderThin(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
