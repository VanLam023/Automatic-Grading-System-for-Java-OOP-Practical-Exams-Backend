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
 * <p>Questions belong to an {@code ExamPaper} and are cascaded on ExamPaper delete via DB constraint.
 * This repository provides bulk-delete for the overwrite flow (BR-09).</p>
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
     * Bulk-deletes all questions belonging to the given exam paper.
     * Used during the overwrite flow (BR-09) to clean up the old paper's questions
     * before inserting new ones. TestCases are cascade-deleted by DB constraint.
     *
     * @param examPaperId the exam paper's UUID whose questions should be removed
     */
    @Modifying
    @Query("DELETE FROM Question q WHERE q.examPaper.examPaperId = :examPaperId")
    void deleteByExamPaper_ExamPaperId(@Param("examPaperId") UUID examPaperId);
}
