package agsfjope.backend.application.dtos.responses.lecturerdashboard;

import agsfjope.backend.core.enums.AppealStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a single row in the "Đơn phúc khảo được phân công" table
 * on the Lecturer Dashboard.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignedAppealResponse {

    /** Appeal primary key. */
    private UUID appealId;

    /** Student full name. */
    private String studentName;

    /** Student MSSV code. */
    private String studentMssv;

    /** Exam name associated with this appeal. */
    private String examName;

    /** Block name within the exam. */
    private String blockName;

    /** When the appeal was assigned to this lecturer. */
    private OffsetDateTime assignedDate;

    /** Deadline for the lecturer to complete the review. */
    private OffsetDateTime deadline;

    /** Current appeal status. */
    private AppealStatus status;
}
