package agsfjope.backend.core.repositories.appeal;

import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.enums.AppealStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * Checks whether an appeal already exists for the given submission.
     * Used to enforce BR-01: each submission can only have one appeal.
     *
     * @param submissionId the submission UUID
     * @return true if an appeal already exists for this submission
     */
    boolean existsBySubmission_SubmissionId(UUID submissionId);

    /**
     * Updates the status of an appeal by its ID.
     * Used by {@code PaymentWebhookProcessor} (SUCCESS → PENDING, FAILED → CANCELLED)
     * and by the payment timeout scheduler.
     *
     * @param appealId the appeal UUID
     * @param status   the new status to set
     */
    @Modifying
    @Query("UPDATE Appeal a SET a.status = :status WHERE a.appealId = :appealId")
    void updateStatus(@Param("appealId") UUID appealId,
                      @Param("status") AppealStatus status);

    // ─── Student — My Appeals ────────────────────────────────────────────────

    /**
     * Finds all appeals submitted by a specific student, ordered by creation date descending.
     *
     * @param studentId the student's user UUID
     * @return list of appeals
     */
    @Query("""
        SELECT a FROM Appeal a
        WHERE a.student.userId = :studentId
        ORDER BY a.createdAt DESC
    """)
    List<Appeal> findByStudentOrderByCreatedAtDesc(@Param("studentId") UUID studentId);

    /**
     * Counts appeals by student and status using native SQL with CAST for PostgreSQL enum.
     *
     * @param studentId the student's user UUID
     * @param status    the appeal status string
     * @return count of matching appeals
     */
    @Query(value = """
            SELECT COUNT(*) FROM Appeals a
            WHERE a.StudentID = :studentId
              AND a.Status = CAST(:status AS appeal_status)
            """,
           nativeQuery = true)
    long countByStudentAndStatus(@Param("studentId") UUID studentId,
                                 @Param("status") String status);

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

    // ─── Staff — Appeal Management ────────────────────────────────────────────

    /**
     * Paged + filtered list of appeals for Exam Staff Appeal Management screen.
     * Filters by status (optional) and keyword (student name, MSSV, exam name).
     * Uses native SQL with CAST for PostgreSQL enum type.
     */
    @Query(value = """
            SELECT a.* FROM Appeals a
            JOIN Users u ON a.StudentID = u.UserID
            JOIN Submissions s ON a.SubmissionID = s.SubmissionID
            JOIN Blocks b ON s.BlockID = b.BlockID
            JOIN Exams e ON b.ExamID = e.ExamID
            WHERE (:status IS NULL OR a.Status = CAST(:status AS appeal_status))
              AND (:keyword = '' OR
                   LOWER(u.FullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(u.MSSV)     LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:semester IS NULL OR e.Semester = :semester)
              AND (:examName IS NULL OR e.Name = :examName)
            ORDER BY a.CreatedAt DESC
            """,
           countQuery = """
            SELECT COUNT(*) FROM Appeals a
            JOIN Users u ON a.StudentID = u.UserID
            JOIN Submissions s ON a.SubmissionID = s.SubmissionID
            JOIN Blocks b ON s.BlockID = b.BlockID
            JOIN Exams e ON b.ExamID = e.ExamID
            WHERE (:status IS NULL OR a.Status = CAST(:status AS appeal_status))
              AND (:keyword = '' OR
                   LOWER(u.FullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(u.MSSV)     LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:semester IS NULL OR e.Semester = :semester)
              AND (:examName IS NULL OR e.Name = :examName)
            """,
           nativeQuery = true)
    Page<Appeal> searchAppealsForStaff(
            @Param("status")  String status,
            @Param("keyword") String keyword,
            @Param("semester") String semester,
            @Param("examName") String examName,
            Pageable pageable);

    /**
     * Counts active appeals (PROCESSING) assigned to a specific lecturer.
     * Used to show workload in the lecturer dropdown on Assign Appeal screen.
     *
     * @param lecturerId the lecturer's UUID
     * @return number of PROCESSING appeals currently assigned
     */
    @Query(value = """
            SELECT COUNT(*) FROM Appeals a
            WHERE a.AssignedLecturerID = :lecturerId
              AND a.Status = CAST('PROCESSING' AS appeal_status)
            """,
           nativeQuery = true)
    long countActiveAppealsByLecturer(@Param("lecturerId") UUID lecturerId);

    // ─── Staff Dashboard ────────────────────────────────────────────────────


    /**
     * Finds only appeals with PENDING status, ordered by creation date descending.
     * Used by Staff Dashboard "Đơn phúc khảo cần xử lý" table.
     */
    @Query(value = """
            SELECT * FROM Appeals a
            WHERE a.Status = CAST('PENDING' AS appeal_status)
            ORDER BY a.CreatedAt DESC
            """,
           nativeQuery = true)
    List<Appeal> findPendingOrderByCreatedAtDesc(
            org.springframework.data.domain.Pageable pageable);

    /**
     * Finds only appeals with PENDING status for a specific semester.
     */
    @Query(value = """
            SELECT a.* FROM Appeals a
            JOIN Submissions s ON a.SubmissionID = s.SubmissionID
            JOIN Blocks b ON s.BlockID = b.BlockID
            JOIN Exams e ON b.ExamID = e.ExamID
            WHERE a.Status = CAST('PENDING' AS appeal_status)
              AND e.Semester = :semester
            ORDER BY a.CreatedAt DESC
            """,
           nativeQuery = true)
    List<Appeal> findPendingBySemesterOrderByCreatedAtDesc(
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

    // ─── Lecturer — Appeal Management ─────────────────────────────────────────

    /**
     * Paged + filtered list of appeals for Lecturer Appeal List screen.
     * Only shows appeals assigned to the specific lecturer.
     */
    @Query(value = """
            SELECT a.* FROM Appeals a
            JOIN Users u ON a.StudentID = u.UserID
            JOIN Submissions s ON a.SubmissionID = s.SubmissionID
            JOIN Blocks b ON s.BlockID = b.BlockID
            JOIN Exams e ON b.ExamID = e.ExamID
            WHERE a.AssignedLecturerID = :lecturerId
              AND a.Status NOT IN (
                CAST('PENDING_PAYMENT' AS appeal_status),
                CAST('PENDING' AS appeal_status)
              )
              AND (:status IS NULL OR a.Status = CAST(:status AS appeal_status))
              AND (:keyword = '' OR
                   LOWER(u.FullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(u.MSSV)     LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(e.Name)     LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY a.CreatedAt DESC
            """,
           countQuery = """
            SELECT COUNT(*) FROM Appeals a
            JOIN Users u ON a.StudentID = u.UserID
            JOIN Submissions s ON a.SubmissionID = s.SubmissionID
            JOIN Blocks b ON s.BlockID = b.BlockID
            JOIN Exams e ON b.ExamID = e.ExamID
            WHERE a.AssignedLecturerID = :lecturerId
              AND a.Status NOT IN (
                CAST('PENDING_PAYMENT' AS appeal_status),
                CAST('PENDING' AS appeal_status)
              )
              AND (:status IS NULL OR a.Status = CAST(:status AS appeal_status))
              AND (:keyword = '' OR
                   LOWER(u.FullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(u.MSSV)     LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(e.Name)     LIKE LOWER(CONCAT('%', :keyword, '%')))
            """,
           nativeQuery = true)
    Page<Appeal> searchAppealsForLecturer(
            @Param("lecturerId") UUID lecturerId,
            @Param("status")  String status,
            @Param("keyword") String keyword,
            Pageable pageable);

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

    // ─── Exam Statistics — Block-level queries (PROC-006) ────────────────────

    /**
     * Counts all appeals belonging to a specific block.
     * Navigates Appeal → Submission → Block.
     *
     * @param blockId the block UUID
     * @return total number of appeals for this block
     */
    @Query(value = """
            SELECT COUNT(*) FROM Appeals a
            JOIN Submissions s ON a.SubmissionID = s.SubmissionID
            WHERE s.BlockID = :blockId
            """,
           nativeQuery = true)
    long countByBlockId(@Param("blockId") UUID blockId);

    /**
     * Counts appeals in a specific block filtered by status.
     * Navigates Appeal → Submission → Block.
     *
     * @param blockId the block UUID
     * @param status  the appeal status string
     * @return count of matching appeals
     */
    @Query(value = """
            SELECT COUNT(*) FROM Appeals a
            JOIN Submissions s ON a.SubmissionID = s.SubmissionID
            WHERE s.BlockID = :blockId
              AND a.Status = CAST(:status AS appeal_status)
            """,
           nativeQuery = true)
    long countByBlockIdAndStatus(@Param("blockId") UUID blockId,
                                  @Param("status") String status);
}
