package agsfjope.backend.core.repositories.appeal.projections;

import java.util.UUID;

/**
 * Projection for aggregated active appeal workload per lecturer.
 */
public interface LecturerAppealWorkloadProjection {
    UUID getLecturerId();
    Long getActiveAppealCount();
}