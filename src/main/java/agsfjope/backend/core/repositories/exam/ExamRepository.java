package agsfjope.backend.core.repositories.exam;

import agsfjope.backend.core.entities.Exam;
import agsfjope.backend.core.enums.ExamStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Exam entity.
 * Extends JpaRepository to provide standard CRUD operations.
 * Placed in the {@code exam} subpackage following the project convention.
 */
public interface ExamRepository extends JpaRepository<Exam, UUID> {

    /**
     * Checks if any active exam already exists for the given semester.
     * Enforces the rule: only one exam per semester.
     *
     * @param semester semester code (e.g., SP24)
     * @return true if an active exam already exists for this semester
     */
    boolean existsBySemesterAndDeletedAtIsNull(String semester);

    /**
     * Checks if any active exam with the given semester exists, excluding a specific exam ID.
     * Used during update to allow same-semester without self-conflict.
     *
     * @param semester semester code
     * @param examId   the exam ID to exclude (the one being updated)
     * @return true if another active exam uses this semester
     */
    boolean existsBySemesterAndDeletedAtIsNullAndExamIdNot(String semester, UUID examId);

    /**
     * Returns all exams that have not been soft-deleted.
     *
     * @return list of active exams
     */
    List<Exam> findAllByDeletedAtIsNull();

    /**
     * Returns a paginated list of active (non-deleted) exams.
     *
     * @param pageable paging and sorting parameters
     * @return page of active exams
     */
    Page<Exam> findAllByDeletedAtIsNull(Pageable pageable);

    /**
     * Searches active exams by name (contains, ignore case), semester (contains, ignore case),
     * and optionally filters by academic year (exact match).
     * Any null/empty parameter is treated as "no filter" via JPQL LOWER/LIKE.
     *
     * @param name         partial exam name to search (null = match all)
     * @param semester     partial semester code to search (null = match all)
     * @param academicYear exact academic year to filter (null = match all)
     * @param pageable     paging and sorting params
     * @return page of matching active exams
     */
    @Query(value = """
            SELECT * FROM Exams e
            WHERE e.DeletedAt IS NULL
              AND (CAST(:name AS TEXT) IS NULL OR lower(e.Name) LIKE lower('%' || CAST(:name AS TEXT) || '%'))
              AND (CAST(:semester AS TEXT) IS NULL OR lower(e.Semester) LIKE lower('%' || CAST(:semester AS TEXT) || '%'))
              AND (CAST(:academicYear AS TEXT) IS NULL OR lower(e.AcademicYear) LIKE lower('%' || CAST(:academicYear AS TEXT) || '%'))
            """,
            countQuery = """
            SELECT COUNT(*) FROM Exams e
            WHERE e.DeletedAt IS NULL
              AND (CAST(:name AS TEXT) IS NULL OR lower(e.Name) LIKE lower('%' || CAST(:name AS TEXT) || '%'))
              AND (CAST(:semester AS TEXT) IS NULL OR lower(e.Semester) LIKE lower('%' || CAST(:semester AS TEXT) || '%'))
              AND (CAST(:academicYear AS TEXT) IS NULL OR lower(e.AcademicYear) LIKE lower('%' || CAST(:academicYear AS TEXT) || '%'))
            """,
            nativeQuery = true)
    Page<Exam> searchExams(
            @Param("name")         String name,
            @Param("semester")     String semester,
            @Param("academicYear") String academicYear,
            Pageable pageable
    );

    /**
     * Returns a non-deleted exam by its ID.
     *
     * @param examId exam identifier
     * @return optional exam
     */
    Optional<Exam> findByExamIdAndDeletedAtIsNull(UUID examId);

    /**
     * Checks if an active (non-deleted) exam exists with the given ID.
     * Used by BlockService to validate the parent exam before listing blocks.
     *
     * @param examId exam identifier
     * @return true if the exam exists and is not deleted
     */
    boolean existsByExamIdAndDeletedAtIsNull(UUID examId);

    /**
     * Checks if any submission exists linked to any block belonging to the given exam.
     * Uses JPQL to navigate Submission → Block → Exam relationship.
     * Used to enforce BR-12: exam can only be deleted if no submissions exist.
     *
     * @param examId exam identifier
     * @return true if at least one submission exists for this exam's blocks
     */
    @Query("SELECT COUNT(s) > 0 FROM Submission s WHERE s.block.exam.examId = :examId")
    boolean existsSubmissionsByExamId(@Param("examId") UUID examId);

    /**
     * Counts active (non-deleted) exams with the specified status.
     * Used by the Admin Dashboard to display the number of currently active (ONGOING) exams.
     *
     * @param status    the exam status to filter by (e.g. ONGOING)
     * @return number of non-soft-deleted exams matching the status
     */
    long countByStatusAndDeletedAtIsNull(ExamStatus status);

    /**
     * Counts active (non-deleted) exams with the specified status, created within a date range.
     * Used by the Admin Dashboard date-filtered overview.
     *
     * @param status the exam status to filter by (e.g. ONGOING)
     * @param from   start of date range (inclusive)
     * @param to     end of date range (inclusive)
     * @return number of non-soft-deleted exams matching the status and date range
     */
    long countByStatusAndDeletedAtIsNullAndCreatedAtBetween(ExamStatus status, OffsetDateTime from, OffsetDateTime to);
}
