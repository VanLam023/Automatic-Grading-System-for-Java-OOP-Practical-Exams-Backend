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

    // ─── Staff Dashboard ────────────────────────────────────────────────────

    /**
     * Finds appeals with PENDING or PROCESSING status, ordered by creation date descending.
     * Uses native SQL with explicit CAST to handle PostgreSQL custom enum type (appeal_status).
     * Used by Staff Dashboard "Đơn phúc khảo cần xử lý" table.
     *
     * @param pageable paging parameters (limit)
     * @return list of matching appeals
     */
    @Query(value = """
            SELECT * FROM Appeals a
            WHERE a.Status IN (CAST('PENDING' AS appeal_status), CAST('PROCESSING' AS appeal_status))
            ORDER BY a.CreatedAt DESC
            """,
           nativeQuery = true)
    List<Appeal> findPendingAndProcessingOrderByCreatedAtDesc(
            org.springframework.data.domain.Pageable pageable);

    /**
     * Finds appeals with PENDING or PROCESSING status for a specific semester.
     * Navigates Appeal → Submission → Block → Exam to check semester.
     * Uses native SQL with explicit CAST for PostgreSQL custom enum type.
     *
     * @param semester semester code to filter by
     * @param pageable paging parameters (limit)
     * @return list of matching appeals
     */
    @Query(value = """
            SELECT a.* FROM Appeals a
            JOIN Submissions s ON a.SubmissionID = s.SubmissionID
            JOIN Blocks b ON s.BlockID = b.BlockID
            JOIN Exams e ON b.ExamID = e.ExamID
            WHERE a.Status IN (CAST('PENDING' AS appeal_status), CAST('PROCESSING' AS appeal_status))
              AND e.Semester = :semester
            ORDER BY a.CreatedAt DESC
            """,
           nativeQuery = true)
    List<Appeal> findPendingAndProcessingBySemesterOrderByCreatedAtDesc(
            @Param("semester") String semester,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Counts appeals with the given status for a specific semester.
     * Navigates Appeal → Submission → Block → Exam to check semester.
     *
     * @param status   the appeal status string
     * @param semester semester code to filter by
     * @return number of matching appeals
     */
    @Query(value = """
            SELECT COUNT(*) FROM Appeals a
            JOIN Submissions s ON a.SubmissionID = s.SubmissionID
            JOIN Blocks b ON s.BlockID = b.BlockID
            JOIN Exams e ON b.ExamID = e.ExamID
            WHERE a.Status = CAST(:status AS appeal_status)
              AND e.Semester = :semester
            """,
           nativeQuery = true)
    long countByStatusAndSemester(@Param("status") String status,
                                  @Param("semester") String semester);

    // ─── Lecturer Dashboard ─────────────────────────────────────────────────

    /**
     * Counts appeals assigned to a specific lecturer with the given status.
     * Uses native SQL with explicit CAST for PostgreSQL custom enum type.
     *
     * @param lecturerId the lecturer's user UUID
     * @param status     the appeal status string (e.g. "PROCESSING")
     * @return count of matching appeals
     */
    @Query(value = """
            SELECT COUNT(*) FROM Appeals a
            WHERE a.AssignedLecturerID = :lecturerId
              AND a.Status = CAST(:status AS appeal_status)
            """,
           nativeQuery = true)
    long countByAssignedLecturerAndStatus(@Param("lecturerId") UUID lecturerId,
                                          @Param("status") String status);

    /**
     * Counts appeals assigned to a lecturer with status PROCESSING whose deadline has passed.
     * Used for the "Overdue Appeals" card on the Lecturer Dashboard.
     *
     * @param lecturerId the lecturer's user UUID
     * @param now        current timestamp
     * @return count of overdue appeals
     */
    @Query(value = """
            SELECT COUNT(*) FROM Appeals a
            WHERE a.AssignedLecturerID = :lecturerId
              AND a.Status = CAST('PROCESSING' AS appeal_status)
              AND a.DeadlineAt < :now
            """,
           nativeQuery = true)
    long countOverdueByAssignedLecturer(@Param("lecturerId") UUID lecturerId,
                                        @Param("now") OffsetDateTime now);

    /**
     * Counts completed reviews for a lecturer (COMPLETED, APPROVED, or DENIED).
     * Used for the "Completed Reviews" card on the Lecturer Dashboard.
     *
     * @param lecturerId the lecturer's user UUID
     * @return count of completed reviews
     */
    @Query(value = """
            SELECT COUNT(*) FROM Appeals a
            WHERE a.AssignedLecturerID = :lecturerId
              AND a.Status IN (
                CAST('COMPLETED' AS appeal_status),
                CAST('APPROVED'  AS appeal_status),
                CAST('DENIED'    AS appeal_status)
              )
            """,
           nativeQuery = true)
    long countCompletedReviewsByAssignedLecturer(@Param("lecturerId") UUID lecturerId);

    /**
     * Finds all appeals assigned to a specific lecturer, ordered by assignedAt descending.
     * Used for the "Đơn phúc khảo được phân công" table on the Lecturer Dashboard.
     *
     * @param lecturerId the lecturer's user UUID
     * @param pageable   paging parameters
     * @return list of assigned appeals
     */
    @Query("""
        SELECT a FROM Appeal a
        WHERE a.assignedLecturer.userId = :lecturerId
        ORDER BY a.assignedAt DESC
    """)
    List<Appeal> findByAssignedLecturerOrderByAssignedAtDesc(
            @Param("lecturerId") UUID lecturerId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Finds appeals assigned to a specific lecturer filtered by status, ordered by assignedAt descending.
     * Uses native SQL with explicit CAST for PostgreSQL custom enum type.
     *
     * @param lecturerId the lecturer's user UUID
     * @param status     the appeal status string to filter by
     * @param pageable   paging parameters
     * @return list of assigned appeals matching the given status
     */
    @Query(value = """
            SELECT * FROM Appeals a
            WHERE a.AssignedLecturerID = :lecturerId
              AND a.Status = CAST(:status AS appeal_status)
            ORDER BY a.AssignedAt DESC
            """,
           nativeQuery = true)
    List<Appeal> findByAssignedLecturerAndStatusOrderByAssignedAtDesc(
            @Param("lecturerId") UUID lecturerId,
            @Param("status") String status,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Finds PROCESSING appeals assigned to a lecturer, ordered by deadlineAt ascending.
     * Used for the "Deadline sắp tới" section on the Lecturer Dashboard.
     *
     * @param lecturerId the lecturer's user UUID
     * @param pageable   paging parameters
     * @return list of appeals sorted by deadline
     */
    @Query("""
        SELECT a FROM Appeal a
        WHERE a.assignedLecturer.userId = :lecturerId
          AND a.status = agsfjope.backend.core.enums.AppealStatus.PROCESSING
        ORDER BY a.deadlineAt ASC
    """)
    List<Appeal> findProcessingByAssignedLecturerOrderByDeadlineAsc(
            @Param("lecturerId") UUID lecturerId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Counts approved appeals for a lecturer.
     * Uses native SQL with explicit CAST for PostgreSQL custom enum type.
     *
     * @param lecturerId the lecturer's user UUID
     * @return count of approved appeals
     */
    @Query(value = """
            SELECT COUNT(*) FROM Appeals a
            WHERE a.AssignedLecturerID = :lecturerId
              AND a.Status = CAST('APPROVED' AS appeal_status)
            """,
           nativeQuery = true)
    long countApprovedByAssignedLecturer(@Param("lecturerId") UUID lecturerId);

    /**
     * Counts denied appeals for a lecturer.
     * Uses native SQL with explicit CAST for PostgreSQL custom enum type.
     *
     * @param lecturerId the lecturer's user UUID
     * @return count of denied appeals
     */
    @Query(value = """
            SELECT COUNT(*) FROM Appeals a
            WHERE a.AssignedLecturerID = :lecturerId
              AND a.Status = CAST('DENIED' AS appeal_status)
            """,
           nativeQuery = true)
    long countDeniedByAssignedLecturer(@Param("lecturerId") UUID lecturerId);
}

