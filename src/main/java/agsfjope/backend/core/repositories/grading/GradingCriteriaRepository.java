package agsfjope.backend.core.repositories.grading;

import agsfjope.backend.core.entities.GradingCriteria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link GradingCriteria} entity.
 *
 * <p>Provides queries to retrieve criteria for a question,
 * ordered by {@code displayOrder} for consistent evaluation order.</p>
 */
@Repository
public interface GradingCriteriaRepository extends JpaRepository<GradingCriteria, UUID> {

    /**
     * Find all structural criteria for a question, ordered by displayOrder ASC.
     * Called by the grading pipeline before evaluating each answer.
     */
    List<GradingCriteria> findByQuestion_QuestionIdOrderByDisplayOrderAsc(UUID questionId);

    /**
     * Check whether a question has any grading criteria defined.
     * Used to decide whether to run structural analysis.
     *
     * @param questionId the question's UUID
     * @return true if at least one criterion exists
     */
    boolean existsByQuestion_QuestionId(UUID questionId);
}
