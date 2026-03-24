package agsfjope.backend.application.dtos.responses.exam;

import agsfjope.backend.core.enums.ExamStatus;
import agsfjope.backend.core.enums.GradingMode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing full exam information.
 *
 * <p>The {@code status} field is computed dynamically from {@code startTime} and {@code endTime}
 * at serialization time — it is NOT stored in the database. This ensures the status is
 * always accurate relative to the current server time.</p>
 *
 * <ul>
 *   <li>now &lt; startTime → {@link ExamStatus#UPCOMING}</li>
 *   <li>startTime &le; now &le; endTime → {@link ExamStatus#ONGOING}</li>
 *   <li>now &gt; endTime → {@link ExamStatus#COMPLETED}</li>
 * </ul>
 */
@Data
@Builder
public class ExamResponse {

    /** Unique identifier of the exam. */
    private UUID examId;

    /** Name of the exam. */
    private String name;

    /** Semester code (e.g., SP24, SU25). */
    private String semester;

    /** Academic year (e.g., 2025-2026). */
    private String academicYear;

    /** Optional exam description. */
    private String description;

    /** Scheduled start time. */
    private OffsetDateTime startTime;

    /** Scheduled end time. */
    private OffsetDateTime endTime;

    /** Grading algorithm applied to all submissions. */
    private GradingMode gradingMode;

    /** Username of the staff member who created this exam. */
    private String createdBy;

    /** Timestamp when this exam was first created. */
    private OffsetDateTime createdAt;

    /**
     * Computes the current lifecycle status dynamically based on system time.
     * This field is NOT persisted to the database.
     *
     * @return {@link ExamStatus} derived from {@code startTime} and {@code endTime}
     */
    @JsonProperty("status")
    public ExamStatus getStatus() {
        if (startTime == null || endTime == null) {
            return ExamStatus.UPCOMING;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(startTime)) {
            return ExamStatus.UPCOMING;
        } else if (!now.isAfter(endTime)) {
            return ExamStatus.ONGOING;
        } else {
            return ExamStatus.COMPLETED;
        }
    }
}
