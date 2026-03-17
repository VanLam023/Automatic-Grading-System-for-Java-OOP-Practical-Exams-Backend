package agsfjope.backend.application.dtos.responses.exam;

import agsfjope.backend.core.enums.ExamStatus;
import agsfjope.backend.core.enums.GradingMode;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO representing full exam information.
 * Returned from all exam-related endpoints.
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

    /** Current exam lifecycle status. */
    private ExamStatus status;

    /** Grading algorithm applied to all submissions. */
    private GradingMode gradingMode;

    /** Username of the staff member who created this exam. */
    private String createdBy;

    /** Timestamp when this exam was first created. */
    private OffsetDateTime createdAt;
}
