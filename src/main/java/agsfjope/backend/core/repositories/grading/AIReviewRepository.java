package agsfjope.backend.core.repositories.grading;

import agsfjope.backend.core.entities.AIReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link AIReview} entity.
 *
 * <p>Each answer has at most one AI review — enforced by the
 * {@code @OneToOne} mapping on {@link agsfjope.backend.core.entities.AIReview}.</p>
 */
@Repository
public interface AIReviewRepository extends JpaRepository<AIReview, UUID> {

    /**
     * Find the AI review for a specific answer.
     *
     * @param answerId the answer's UUID
     * @return optional AI review
     */
    Optional<AIReview> findByAnswer_AnswerId(UUID answerId);

    /**
     * Find all AI reviews for all answers in a submission.
     * Used by {@code GradingQueryService} to build the full submission result view.
     *
     * @param submissionId the submission's UUID
     * @return list of AI reviews (one per answer that was reviewed)
     */
    @Query("SELECT ar FROM AIReview ar WHERE ar.answer.submission.submissionId = :submissionId")
    java.util.List<AIReview> findByAnswer_Submission_SubmissionId(@Param("submissionId") UUID submissionId);

    /**
     * Delete all AI reviews for a given submission.
     * Used when re-grading a submission.
     *
     * @param submissionId the submission's UUID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AIReview ar WHERE ar.answer.submission.submissionId = :submissionId")
    void deleteByAnswer_Submission_SubmissionId(@Param("submissionId") UUID submissionId);
}
