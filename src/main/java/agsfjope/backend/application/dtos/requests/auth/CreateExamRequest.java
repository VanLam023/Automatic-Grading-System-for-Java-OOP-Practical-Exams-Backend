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

    private String createdBy;
    /** Grading mode used for the exam */
    @NotNull
    private GradingMode gradingMode;

}
