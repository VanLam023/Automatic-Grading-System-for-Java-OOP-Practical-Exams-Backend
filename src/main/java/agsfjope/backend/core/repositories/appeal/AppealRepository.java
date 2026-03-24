package agsfjope.backend.core.repositories.appeal;

import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.enums.AppealStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Appeal} entities.
 * Provides query methods used by appeal processing and the deadline scheduler.
 */
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
}
