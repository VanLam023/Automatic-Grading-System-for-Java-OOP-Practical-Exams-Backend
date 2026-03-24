package agsfjope.backend.core.repositories.appeal;

import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.enums.AppealStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Appeal} entities.
 * Provides query methods used by appeal processing, the deadline scheduler,
 * and the Admin Dashboard.
 */
@Repository
public interface AppealRepository extends JpaRepository<Appeal, UUID> {

    /**
     * Finds an appeal by the associated submission ID.
     *
     * @param submissionId the submission UUID
     * @return the appeal if it exists
     */
    Optional<Appeal> findBySubmission_SubmissionId(UUID submissionId);

    /**
     * Finds all appeals in PROCESSING status whose deadline falls exactly on
     * or between two points in time.
     * Used by the deadline-reminder scheduler to notify lecturers 2 days before.
     *
     * @param from inclusive start of the window
     * @param to   inclusive end of the window
     * @return list of matching appeals
     */
    @Query("""
        SELECT a FROM Appeal a
        WHERE a.status = :status
          AND a.deadlineAt BETWEEN :from AND :to
    """)
    List<Appeal> findByStatusAndDeadlineAtBetween(
            @Param("status") AppealStatus status,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    /**
     * Finds all appeals in PROCESSING status whose deadline is before the given time.
     * Used by the overdue scheduler to identify and alert on expired appeals.
     *
     * @param now the current timestamp
     * @return list of overdue appeals
     */
    @Query("""
        SELECT a FROM Appeal a
        WHERE a.status = :status
          AND a.deadlineAt < :now
    """)
    List<Appeal> findByStatusAndDeadlineAtBefore(
            @Param("status") AppealStatus status,
            @Param("now") OffsetDateTime now);

    /**
     * Counts the number of appeals with the specified status.
     * Uses native SQL with explicit CAST to handle PostgreSQL custom enum type (appeal_status).
     * Used by the Admin Dashboard to display the count of pending appeals.
     *
     * @param status the appeal status string to filter by (e.g. "PENDING")
     * @return number of appeals matching the given status
     */
    @Query(value = "SELECT COUNT(*) FROM Appeals a WHERE a.Status = CAST(:status AS appeal_status)",
           nativeQuery = true)
    long countByStatus(@Param("status") String status);

    /**
     * Counts appeals with the specified status created within a date range.
     * Uses native SQL with explicit CAST to handle PostgreSQL custom enum type (appeal_status).
     * Used by the Admin Dashboard date-filtered overview.
     *
     * @param status the appeal status string to filter by (e.g. "PENDING")
     * @param from   start of date range (inclusive)
     * @param to     end of date range (inclusive)
     * @return number of appeals matching the given status and date range
     */
    @Query(value = "SELECT COUNT(*) FROM Appeals a WHERE a.Status = CAST(:status AS appeal_status) AND a.CreatedAt BETWEEN :from AND :to",
           nativeQuery = true)
    long countByStatusAndCreatedAtBetween(@Param("status") String status,
                                          @Param("from") OffsetDateTime from,
                                          @Param("to") OffsetDateTime to);
}
