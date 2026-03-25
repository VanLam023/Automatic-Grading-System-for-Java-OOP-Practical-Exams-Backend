package agsfjope.backend.application.dtos.responses.lecturerdashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the "Thống kê" donut chart on the Lecturer Dashboard.
 * <p>
 * Shows the breakdown of completed reviews into Approved vs Denied.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewStatsResponse {

    /** Total number of reviews completed (COMPLETED + APPROVED + DENIED). */
    private long totalReviews;

    /** Number of appeals that were approved (APPROVED). */
    private long approvedCount;

    /** Percentage of approved reviews (0.0 – 100.0, rounded to 1 decimal). */
    private double approvedPercentage;

    /** Number of appeals that were denied (DENIED). */
    private long deniedCount;

    /** Percentage of denied reviews (0.0 – 100.0, rounded to 1 decimal). */
    private double deniedPercentage;
}
