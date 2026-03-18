package agsfjope.backend.core.repositories.exam;

import agsfjope.backend.core.entities.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
