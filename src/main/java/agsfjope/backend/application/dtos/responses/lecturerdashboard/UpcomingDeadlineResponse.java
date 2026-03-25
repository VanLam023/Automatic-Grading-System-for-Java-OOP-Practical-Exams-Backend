package agsfjope.backend.application.dtos.responses.lecturerdashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a single item in the "Deadline sắp tới" list
 * on the Lecturer Dashboard.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpcomingDeadlineResponse {

    /** Appeal primary key. */
    private UUID appealId;

    /** Exam name associated with this appeal. */
    private String examName;

    /** Student full name. */
    private String studentName;

    /** Deadline timestamp for this review. */
    private OffsetDateTime deadline;

    /**
     * Human-readable urgency label shown in the UI:
     * <ul>
     *   <li>{@code "CẦN XỬ LÝ NGAY"} — deadline already passed</li>
     *   <li>{@code "TRONG 2 NGÀY TỚI"} — deadline within next 48 hours</li>
     *   <li>{@code "SẮP TỚI"} — deadline more than 48 hours away</li>
     * </ul>
     */
    private String urgencyLabel;
}
