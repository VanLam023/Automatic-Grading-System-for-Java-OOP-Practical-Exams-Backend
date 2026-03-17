package agsfjope.backend.application.dtos.requests.auth;


import agsfjope.backend.core.enums.GradingMode;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class CreateExamRequest {


    /** Name of the exam */
    @NotBlank
    private String name;



    @NotBlank
    @Pattern(regexp = "^(SP|SU|FA)\\d{2}$", message = "Semester must follow format SP24, SU25, FA26, etc.")
    private String semester;


    /** Academic year (e.g., 2025-2026) */
    @NotBlank
    @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "Academic year must follow format YYYY-YYYY")
    private String academicYear;


    /** Optional description of the exam */
    @Size(max = 2000)
    private String description;


    /** Exam start time */
    @NotNull
    @Future(message = "Start time must be in the future")
    private OffsetDateTime startTime;


    /** Exam end time */
    @NotNull
    private OffsetDateTime endTime;


    @NotNull
    private GradingMode gradingMode;

    @AssertTrue(message = "End time must be after start time")
    public boolean isValidTimeRange() {
        if (startTime == null || endTime == null) return true;
        return endTime.isAfter(startTime);
    }

    @AssertTrue(message = "Academic year must be a valid range (e.g., 2025-2026)")
    public boolean isValidAcademicYearRange() {
        if (academicYear == null) return true;
        try {
            String[] parts = academicYear.split("-");
            int start = Integer.parseInt(parts[0]);
            int end = Integer.parseInt(parts[1]);
            return end == start + 1;
        } catch (Exception e) {
            return false;
        }
    }

}
