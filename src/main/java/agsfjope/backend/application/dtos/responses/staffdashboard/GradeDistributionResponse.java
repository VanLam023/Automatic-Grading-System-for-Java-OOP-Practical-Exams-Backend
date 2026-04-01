package agsfjope.backend.application.dtos.responses.staffdashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for the "Phân bố điểm" bar chart on the Staff Dashboard.
 * <p>
 * Contains the total number of graded submissions and a breakdown by score range.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradeDistributionResponse {

    /** Total number of graded submissions included in the distribution. */
    private long totalGraded;

    /** Ordered list of score-range buckets. */
    private List<ScoreRange> ranges;

    /**
     * A single score-range bucket for the bar chart.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScoreRange {

        /** Display label (e.g. "0-4", "4-6"). */
        private String label;

        /** Number of submissions whose totalScore falls in this range. */
        private long count;

        /** Percentage of total graded submissions in this range (0.0 - 100.0, rounded to 1 decimal). */
        private double percentage;
    }
}
