package agsfjope.backend.core.repositories.grading;

import agsfjope.backend.core.entities.GradingResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link GradingResult} entity.
 *
 * <p>Provides queries to retrieve grading results by submission or block,
 * and check whether a submission has already been graded.</p>
 */
@Repository
public interface GradingResultRepository extends JpaRepository<GradingResult, UUID> {

    /**
     * Find the grading result for a specific submission.
     *
     * @param submissionId the submission's UUID
     * @return optional grading result
     */
    Optional<GradingResult> findBySubmission_SubmissionId(UUID submissionId);

    /**
     * Find all grading results for all submissions in a given block.
     *
     * @param blockId the block's UUID
     * @return list of grading results (one per graded submission)
     */
    @Query("SELECT gr FROM GradingResult gr WHERE gr.submission.block.blockId = :blockId")
    List<GradingResult> findAllBySubmission_Block_BlockId(@Param("blockId") UUID blockId);

    /**
     * Check whether a submission has already been graded.
     *
     * @param submissionId the submission's UUID
     * @return true if a grading result already exists
     */
    boolean existsBySubmission_SubmissionId(UUID submissionId);

    /**
     * Deletes the grading result for a submission via a direct JPQL DELETE statement.
     * Used before re-grading to avoid duplicate key violation on the unique SubmissionID constraint.
     * Executes immediately (bypasses Hibernate entity lifecycle and batch queue).
     *
     * @param submissionId the submission's UUID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM GradingResult gr WHERE gr.submission.submissionId = :submissionId")
    void deleteBySubmission_SubmissionId(@Param("submissionId") UUID submissionId);
}

