package agsfjope.backend.application.dtos.requests.exam;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * DTO for creating a new exam.
 *
 * <p>Notes on omitted fields:</p>
 * <ul>
 *   <li>{@code academicYear}: auto-calculated from the current date by the service layer.</li>
 *   <li>{@code gradingMode}: auto-fetched from the system default config (DEFAULT_GRADING_MODE).
 *       System Admin sets this via /api/admin/config/grading-modes/{mode}/set-default.</li>
 * </ul>
 */
@Data
public class CreateExamRequest {

    /** Name of the exam. */
    @NotBlank(message = "Tên kỳ thi không được để trống")
    private String name;

    /**
     * Semester code following format SP|SU|FA + 2-digit year.
     * Must be unique across all exams (only one exam per semester).
     * Example: SP24, SU25, FA26
     */
    @NotBlank(message = "Học kỳ không được để trống")
    @Pattern(regexp = "^(SP|SU|FA)\\d{2}$", message = "Học kỳ phải theo định dạng SP24, SU25, FA26, ...")
    private String semester;

    /** Optional description of the exam. */
    @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự")
    private String description;

    /**
     * Exam start time.
     * Must be in the future, within the current academic year,
     * and the total duration (start → end) cannot exceed 4.5 months.
     */
    @NotNull(message = "Thời gian bắt đầu không được để trống")
    @Future(message = "Thời gian bắt đầu phải ở tương lai")
    private OffsetDateTime startTime;

    /**
     * Exam end time.
     * Must be after start time, within the current academic year,
     * and within 4.5 months from start time.
     */
    @NotNull(message = "Thời gian kết thúc không được để trống")
    private OffsetDateTime endTime;

    /**
     * Validates that end time is strictly after start time.
     *
     * @return true if time range is valid
     */
    @AssertTrue(message = "Thời gian kết thúc phải sau thời gian bắt đầu")
    public boolean isValidTimeRange() {
        if (startTime == null || endTime == null) return true;
        return endTime.isAfter(startTime);
    }
}
