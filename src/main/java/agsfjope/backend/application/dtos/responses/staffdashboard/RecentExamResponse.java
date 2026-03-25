package agsfjope.backend.application.dtos.responses.staffdashboard;

import agsfjope.backend.core.enums.ExamStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for a single row in the "Kỳ thi gần đây" table on the Staff Dashboard.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentExamResponse {

    /** Exam primary key. */
    private UUID examId;

    /** Exam display name. */
    private String name;

    /** Semester code (e.g. "Summer 2024"). */
    private String semester;

    /** Current exam status (UPCOMING, ONGOING, COMPLETED). */
    private ExamStatus status;
}
