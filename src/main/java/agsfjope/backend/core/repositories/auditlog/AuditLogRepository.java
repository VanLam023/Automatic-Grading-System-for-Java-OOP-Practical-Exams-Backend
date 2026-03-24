package agsfjope.backend.core.repositories.auditlog;

import agsfjope.backend.core.entities.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link AuditLog} entities (TRG-008, BR-48, SCH-004).
 * <p>
 * Uses {@link JpaSpecificationExecutor} for dynamic filtering —
 * the controller accepts optional query params (action, entityType, userId, date range)
 * that are composed into a Specification at the service layer.
 * </p>
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>,
        JpaSpecificationExecutor<AuditLog> {

    /**
     * SCH-004: Deletes audit log records older than the given cutoff date.
     * Called weekly by {@code AuditLogCleanupScheduler} based on the
     * retention period configured by System Admin.
     *
     * @param cutoff timestamp — logs created before this will be deleted
     * @return number of deleted records
     */
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") OffsetDateTime cutoff);

    /**
     * Returns the 10 most recent audit log entries, sorted newest-first.
     * Used by the Admin Dashboard "Recent Activities" table.
     *
     * @return list of up to 10 most recent audit logs
     */
    List<AuditLog> findTop10ByOrderByCreatedAtDesc();

    /**
     * Counts audit log entries created after the given timestamp.
     * Used by the Admin Dashboard to build time-series activity chart data.
     *
     * @param after the earliest timestamp to include
     * @return number of audit log entries since the given timestamp
     */
    long countByCreatedAtAfter(OffsetDateTime after);

    /**
     * Returns the 10 most recent audit log entries within a date range, sorted newest-first.
     * Used by the Admin Dashboard "Recent Activities" table when a date filter is applied.
     *
     * @param from start of date range (inclusive)
     * @param to   end of date range (inclusive)
     * @return list of up to 10 most recent audit logs within the given range
     */
    List<AuditLog> findTop10ByCreatedAtBetweenOrderByCreatedAtDesc(OffsetDateTime from, OffsetDateTime to);
}
