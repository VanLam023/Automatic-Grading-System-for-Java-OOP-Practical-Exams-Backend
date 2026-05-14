package agsfjope.backend.core.repositories.grading;

import agsfjope.backend.core.entities.CriteriaResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

/**
 * Repository for {@link CriteriaResult} entity.
 *
 * <p>Mirrors the pattern of {@link TestCaseResultRepository}.
 * OOP score per answer = SUM(earnedScore) of its criteria_results.</p>
 */
@Repository
public interface CriteriaResultRepository extends JpaRepository<CriteriaResult, UUID> {

    /**
     * Find all criteria results for a specific answer, ordered by displayOrder ASC.
     * Used by GradingQueryService to build the per-answer OOP detail view.
     *
     * @param answerId the answer's UUID
     * @return ordered list of criteria results
     */
    List<CriteriaResult> findByAnswer_AnswerIdOrderByCriteria_DisplayOrderAsc(UUID answerId);

    /**
     * Find all criteria results for all answers in a submission.
     * Used by GradingQueryService to build the full submission result view in one query.
     *
     * @param submissionId the submission's UUID
     * @return list of criteria results
     */
    @Query("SELECT cr FROM CriteriaResult cr JOIN FETCH cr.criteria JOIN FETCH cr.answer WHERE cr.answer.submission.submissionId = :submissionId ORDER BY cr.criteria.displayOrder ASC")
    List<CriteriaResult> findByAnswer_Submission_SubmissionId(@Param("submissionId") UUID submissionId);

    /**
     * Delete all criteria results for a given submission.
     * Called before re-grading to avoid duplicate unique constraint violations.
     *
     * @param submissionId the submission's UUID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM CriteriaResult cr WHERE cr.answer.submission.submissionId = :submissionId")
    void deleteByAnswer_Submission_SubmissionId(@Param("submissionId") UUID submissionId);

    /**
     * Find all criteria results for all answers in a block.
     * Used by ExamStatisticsServiceImpl to compute per-criterion violation statistics
     * from deterministic JavaParser/Reflection grading results (replaces AI JSON parsing).
     *
     * @param blockId the block's UUID
     * @return list of criteria results with criteria eagerly fetched
     */
    @Query("SELECT cr FROM CriteriaResult cr " +
           "JOIN FETCH cr.criteria " +
           "WHERE cr.answer.submission.block.blockId = :blockId")
    List<CriteriaResult> findAllByBlockId(@Param("blockId") UUID blockId);

    /**
     * Count total criteria results evaluated for a block.
     * Used as the denominator when computing violation rates.
     *
     * @param blockId the block's UUID
     * @return total number of CriteriaResult rows for this block
     */
    @Query("SELECT COUNT(cr) FROM CriteriaResult cr WHERE cr.answer.submission.block.blockId = :blockId")
    long countByBlockId(@Param("blockId") UUID blockId);

    /**
     * Count failed criteria results for a block, grouped by criterionType and description.
     * Returns rows: [criterionType (String), description (String), failCount (Long)].
     * Used by ExamStatisticsServiceImpl to build per-criterion violation stats.
     *
     * @param blockId the block's UUID
     * @return aggregated failure data
     */
    @Query("SELECT cr.criteria.criterionType, cr.criteria.description, COUNT(cr) " +
           "FROM CriteriaResult cr " +
           "WHERE cr.answer.submission.block.blockId = :blockId AND cr.passed = false " +
           "GROUP BY cr.criteria.criterionType, cr.criteria.description " +
           "ORDER BY cr.criteria.criterionType")
    List<Object[]> countFailedByCriterionTypeAndDescription(@Param("blockId") UUID blockId);
}
