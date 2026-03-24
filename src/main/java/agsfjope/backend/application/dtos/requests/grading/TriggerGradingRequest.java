package agsfjope.backend.application.dtos.requests.grading;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Request body for triggering the grading process.
 *
 * <p>3 modes:
 * <ul>
 *   <li>{@code submissionIds = null}: grade ALL SUBMITTED submissions in the block</li>
 *   <li>{@code submissionIds = [id1, id2]}: grade only selected submissions</li>
 *   <li>Use single-submission endpoint: {@code POST .../trigger/{submissionId}}</li>
 * </ul>
 * </p>
 */
@Data
public class TriggerGradingRequest {

    /**
     * The block whose submissions should be graded.
     */
    @NotNull(message = "blockId is required")
    private UUID blockId;

    /**
     * Optional list of specific submission IDs to grade.
     * If null or empty, ALL SUBMITTED submissions in the block will be graded.
     */
    private List<UUID> submissionIds;
}
