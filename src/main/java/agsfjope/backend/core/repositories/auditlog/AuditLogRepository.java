package agsfjope.backend.core.repositories.auditlog;

import agsfjope.backend.core.entities.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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
}
