package agsfjope.backend.application.dtos.requests.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents one student row parsed from the imported Excel file.
 * Each instance corresponds to a single row: Email (col A), FullName (col B), MSSV (col C).
 * This DTO is internal — used by ExcelStudentParser → UserManagementServiceImpl.
 *
 * @param email    the student's FPT email (e.g. duyntse170601@fpt.edu.vn)
 * @param fullName the student's full name in Vietnamese (e.g. Nguyễn Thái Duy)
 * @param mssv     the student's student ID (e.g. SE170601)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportStudentRequest {

    /** Student FPT email — used to derive username and as the unique login email. */
    private String email;

    /** Full name in Vietnamese, stored as-is in the FullName column. */
    private String fullName;

    /** Student ID (MSSV) — unique identifier, e.g. SE170601. */
    private String mssv;
}
