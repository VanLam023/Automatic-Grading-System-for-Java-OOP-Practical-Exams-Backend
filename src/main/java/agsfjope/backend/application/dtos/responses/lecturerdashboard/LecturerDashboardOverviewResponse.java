package agsfjope.backend.application.dtos.responses.lecturerdashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the Lecturer Dashboard overview panel.
 * <p>
 * Contains the three summary cards for the currently logged-in lecturer:
 * appeals assigned to them, completed reviews, and overdue appeals.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LecturerDashboardOverviewResponse {

    /** Number of appeals currently assigned to this lecturer in PROCESSING status. */
    private long assignedAppeals;

    /** Number of appeals this lecturer has finished (COMPLETED, APPROVED, or DENIED). */
    private long completedReviews;

    /** Number of PROCESSING appeals whose deadlineAt has already passed. */
    private long overdueAppeals;
}
