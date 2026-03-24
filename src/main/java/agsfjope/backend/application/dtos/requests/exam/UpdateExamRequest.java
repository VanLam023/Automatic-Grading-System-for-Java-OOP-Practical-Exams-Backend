package agsfjope.backend.application.dtos.requests.exam;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * DTO for updating an existing exam.
 * All updatable fields are optional — only non-null fields will be applied.
 */
@Data
public class UpdateExamRequest {

    /** Updated name of the exam. */
    @Size(max = 255, message = "Tên kỳ thi không được vượt quá 255 ký tự")
    private String name;

    /**
     * Updated semester code.
     * Example: SP24, SU25, FA26
     */
    @Pattern(regexp = "^(SP|SU|FA)\\d{2}$", message = "Học kỳ phải theo định dạng SP24, SU25, FA26, ...")
    private String semester;

    /**
     * Updated academic year.
     * Example: 2025-2026
     */
    @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "Năm học phải theo định dạng YYYY-YYYY")
    private String academicYear;

    /** Updated description. */
    @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự")
    private String description;

    /** Updated exam start time. */
    private OffsetDateTime startTime;

    /** Updated exam end time. */
    private OffsetDateTime endTime;

    /**
     * Validates that end time is strictly after start time when both are provided.
     *
     * @return true if time range is valid or either field is null
     */
    @AssertTrue(message = "Thời gian kết thúc phải sau thời gian bắt đầu")
    public boolean isValidTimeRange() {
        if (startTime == null || endTime == null) return true;
        return endTime.isAfter(startTime);
    }
}
