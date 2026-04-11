package agsfjope.backend.application.dtos.responses.staffdashboard;

import agsfjope.backend.core.enums.AppealStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a single row in the "Đơn phúc khảo cần xử lý" table on the Staff Dashboard.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingAppealResponse {

    /** Appeal primary key. */
    private UUID appealId;

    /** Student full name. */
    private String studentName;

    /** Student MSSV code. */
    private String studentMssv;

    /** Exam name associated with this appeal. */
    private String examName;

    /** Semester associated with this appeal. */
    private String semester;

    /** Current appeal status (PENDING, PROCESSING, etc.). */
    private AppealStatus status;

    /** When the appeal was created. */
    private OffsetDateTime createdAt;
}
