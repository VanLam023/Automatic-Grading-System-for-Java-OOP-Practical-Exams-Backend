package agsfjope.backend.core.repositories.submission.projections;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Flat projection for one submission row in the Exam Staff block submissions table.
 * Uses string getters for enum columns to avoid native-query enum mapping issues.
 */
public interface SubmissionListRowProjection {
    UUID getSubmissionId();
    String getFileName();
    Long getFileSizeBytes();
    String getSubmissionStatus();
    OffsetDateTime getSubmittedAt();

    UUID getStudentId();
    String getStudentName();
    String getStudentCode();
    String getStudentEmail();

    UUID getGradingResultId();
    String getGradingStatus();
    BigDecimal getTotalScore();
    BigDecimal getMaxScore();
    OffsetDateTime getGradedAt();
}