package agsfjope.backend.application.dtos.requests.block;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * DTO for updating an existing block's exam schedule.
 *
 * <p>Blocks cannot be created or deleted via API (BR-08) —
 * they are auto-created when an exam is created.</p>
 *
 * <p>All 3 time fields (examDate, startTime, endTime) must be provided together.
 * Service layer additionally validates that the block times fall within
 * the parent exam's time window.</p>
 */
@Data
public class UpdateBlockRequest {

    /** The date on which this block's exam takes place. */
    @NotNull(message = "Ngày thi không được để trống")
    @FutureOrPresent(message = "Ngày thi không được nhỏ hơn ngày hiện tại")
    private LocalDate examDate;

    /** Start time when students can begin submitting. */
    @NotNull(message = "Thời gian bắt đầu không được để trống")
    private OffsetDateTime startTime;

    /** End time when submission closes. */
    @NotNull(message = "Thời gian kết thúc không được để trống")
    private OffsetDateTime endTime;

    /**
     * Bean validation: endTime must be strictly after startTime.
     * DB also enforces this via CHK_BlockTime constraint.
     */
    @AssertTrue(message = "Thời gian kết thúc phải sau thời gian bắt đầu")
    public boolean isValidTimeRange() {
        if (startTime == null || endTime == null) return true;
        return endTime.isAfter(startTime);
    }
}
