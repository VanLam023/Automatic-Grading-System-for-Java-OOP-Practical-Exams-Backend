package agsfjope.backend.application.dtos.responses.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for the "Hoạt động hệ thống" (System Activity) line chart
 * on the Admin Dashboard.
 * <p>
 * Contains a list of time-series data points where each point represents
 * the number of system actions that occurred within a specific time bucket
 * (hourly for the 24h view, or daily for the 7-day view).
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemActivityResponse {

    /**
     * The time period represented by this response.
     * Either {@code "24h"} (hourly buckets) or {@code "7d"} (daily buckets).
     */
    private String period;

    /** Ordered list of time-series data points from oldest to newest. */
    private List<ActivityPoint> dataPoints;

    /**
     * A single data point in the system activity time-series chart.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActivityPoint {

        /**
         * Human-readable time label for this data point.
         * Format: {@code "HH:00"} for 24h view, {@code "dd/MM"} for 7d view.
         */
        private String label;

        /** Number of audit log entries recorded during this time bucket. */
        private long count;
    }
}
