package agsfjope.backend.core.repositories.submission;

import agsfjope.backend.core.entities.Answer;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link Answer} entity.
 *
 * <p>An Answer maps a student's submitted file (jar + source) to a specific Question
 * within a Submission. Created during the submission parse phase and used during grading.</p>
 */
@Repository
public interface AnswerRepository extends JpaRepository<Answer, UUID> {

    /**
     * Returns all answers for a given submission, ordered by question number ascending.
     *
     * <p>Used when returning submission detail response or preparing grading input.</p>
     *
     * @param submissionId the submission UUID
     * @return ordered list of answers
     */
    List<Answer> findBySubmission_SubmissionIdOrderByQuestion_QuestionNumberAsc(UUID submissionId);

    /**
     * Deletes all answers belonging to a submission.
     *
     * <p>Used during resubmit (BR-17): the old submission's answers must be fully removed
     * before the new submission is saved.</p>
     *
     * @param submissionId the submission UUID whose answers should be deleted
     */
    @Modifying
    @Transactional
    void deleteBySubmission_SubmissionId(UUID submissionId);
}
