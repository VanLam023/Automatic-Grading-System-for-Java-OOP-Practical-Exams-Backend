package agsfjope.backend.core.repositories.exampaper;

import agsfjope.backend.core.entities.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link Question} entity.
 *
 * <p>Questions belong to an {@code ExamPaper} and are cascade-deleted when the paper is deleted.
 * This repository provides queries needed for both ExamPaper management and Submission parsing.</p>
 */
@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    /**
     * Returns all questions for the given exam paper, ordered by question number ascending.
     *
     * @param examPaperId the exam paper's UUID
     * @return ordered list of questions
     */
    List<Question> findByExamPaper_ExamPaperIdOrderByQuestionNumberAsc(UUID examPaperId);

    /**
     * Returns all questions for the exam paper associated with the given block,
     * ordered by question number ascending.
     *
     * <p>Used during submission processing to resolve which questions exist for a block
     * without needing to look up the ExamPaper ID separately.</p>
     *
     * @param blockId the block's UUID
     * @return ordered list of questions
     */
    List<Question> findByExamPaper_Block_BlockIdOrderByQuestionNumberAsc(UUID blockId);

    /**
     * Bulk-deletes all questions belonging to the given exam paper.
     * Used during the overwrite flow (BR-09).
     *
     * @param examPaperId the exam paper's UUID whose questions should be removed
     */
    @Modifying
    @Query("DELETE FROM Question q WHERE q.examPaper.examPaperId = :examPaperId")
    void deleteByExamPaper_ExamPaperId(@Param("examPaperId") UUID examPaperId);
}
