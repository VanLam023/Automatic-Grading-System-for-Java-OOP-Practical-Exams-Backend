package agsfjope.backend.application.dtos.responses.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Summary result returned to the Admin after an Excel import operation.
 * Contains statistics (total/success/skipped counts) and a detailed list
 * of every row that was skipped, so the Admin knows which students failed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportStudentResponse {

    /** Total number of data rows read from the Excel file (excluding header). */
    private int totalRows;

    /** Number of student accounts successfully created in the database. */
    private int successCount;

    /** Number of rows skipped because of validation errors or duplicate data. */
    private int skippedCount;

    /**
     * Detailed breakdown of every skipped row.
     * Each entry contains the row number and the reason it was skipped
     * (e.g. "Email already exists", "MSSV already exists", "Invalid email format").
     */
    private List<SkippedRow> skippedDetails;

    /**
     * Represents a single skipped row with its position and reason.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkippedRow {

        /** 1-based row number in the Excel file (row 2 = first data row). */
        private int rowNumber;

        /** Email value from this row, used to identify which student was skipped. */
        private String email;

        /** Human-readable reason why this row was skipped. */
        private String reason;
    }
}
