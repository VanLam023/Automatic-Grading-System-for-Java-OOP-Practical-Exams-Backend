package agsfjope.backend.core.repositories.appeal;

import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.enums.AppealStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Repository interface for {@link Appeal} entity.
 * Provides standard CRUD operations and domain-specific counting queries
 * used by the Admin Dashboard.
 */
@Repository
public interface AppealRepository extends JpaRepository<Appeal, UUID> {

    /**
     * Counts the number of appeals with the specified status.
     * Used by the Admin Dashboard to display the count of pending appeals.
     *
     * @param status the appeal status to filter by (e.g. PENDING)
     * @return number of appeals matching the given status
     */
    long countByStatus(AppealStatus status);

    /**
     * Counts appeals with the specified status created within a date range.
     * Used by the Admin Dashboard date-filtered overview.
     *
     * @param status the appeal status to filter by (e.g. PENDING)
     * @param from   start of date range (inclusive)
     * @param to     end of date range (inclusive)
     * @return number of appeals matching the given status and date range
     */
    long countByStatusAndCreatedAtBetween(AppealStatus status, OffsetDateTime from, OffsetDateTime to);
}
