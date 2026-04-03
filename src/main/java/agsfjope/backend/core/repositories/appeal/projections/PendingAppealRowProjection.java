package agsfjope.backend.core.repositories.appeal.projections;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Flat projection for the staff dashboard pending appeals table.
 */
public interface PendingAppealRowProjection {
    UUID getAppealId();
    String getStudentName();
    String getStudentMssv();
    String getExamName();
    String getStatus();
    OffsetDateTime getCreatedAt();
}