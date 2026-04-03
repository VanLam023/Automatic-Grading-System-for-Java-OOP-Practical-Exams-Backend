package agsfjope.backend.core.repositories.appeal.projections;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Flat projection for the Exam Staff appeal list page.
 */
public interface StaffAppealListRowProjection {
    UUID getAppealId();
    OffsetDateTime getCreatedAt();
    String getStudentName();
    String getStudentMssv();
    String getExamName();
    String getBlockName();
    String getStatus();
    BigDecimal getOriginalScore();
    BigDecimal getNewScore();
    OffsetDateTime getDeadlineAt();
    String getAssignedLecturerName();
}