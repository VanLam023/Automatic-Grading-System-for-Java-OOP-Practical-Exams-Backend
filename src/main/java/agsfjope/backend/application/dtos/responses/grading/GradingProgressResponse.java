package agsfjope.backend.application.dtos.responses.grading;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Response showing the current grading progress for a block.
 * Used by the staff's grading progress polling API.
 */
@Data
@Builder
public class GradingProgressResponse {

    private UUID blockId;

    /** Total number of submissions in the block. */
    private long totalSubmissions;

    /** Number of submissions that have been fully graded (GRADED status). */
    private long gradedCount;

    /** Number of submissions currently being graded (GRADING status). */
    private long gradingCount;

    /** Number of submissions not yet started (SUBMITTED status). */
    private long pendingCount;

    /** Number of submissions that failed grading and need regrade. */
    private long failedCount;

    /** Overall progress status: IN_PROGRESS or COMPLETED. */
    private String status;

    /** Percentage complete (0–100). */
    private int progressPercent;
}
