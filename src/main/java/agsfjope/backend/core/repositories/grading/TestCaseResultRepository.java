package agsfjope.backend.core.repositories.grading;

import agsfjope.backend.core.entities.TestCaseResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link TestCaseResult} entity.
 *
 * <p>Provides queries to retrieve test case results by answer,
 * ordered by test case number for display purposes.</p>
 */
@Repository
public interface TestCaseResultRepository extends JpaRepository<TestCaseResult, UUID> {

    /**
     * Find all test case results for a specific answer, ordered by test case number ASC.
     *
     * @param answerId the answer's UUID
     * @return ordered list of test case results
     */
    List<TestCaseResult> findByAnswer_AnswerIdOrderByTestCase_TestCaseNumberAsc(UUID answerId);

    /**
     * Find all test case results for all answers in a submission, ordered by test case number.
     * Used by {@code GradingQueryService} to build the full submission result view.
     *
     * @param submissionId the submission's UUID
     * @return ordered list of test case results
     */
    @Query("SELECT tcr FROM TestCaseResult tcr WHERE tcr.answer.submission.submissionId = :submissionId ORDER BY tcr.testCase.testCaseNumber ASC")
    List<TestCaseResult> findByAnswer_Submission_SubmissionIdOrderByTestCase_TestCaseNumberAsc(@Param("submissionId") UUID submissionId);

    /**
     * Delete all test case results for a given submission.
     * Used when re-grading a submission.
     *
     * @param submissionId the submission's UUID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM TestCaseResult tcr WHERE tcr.answer.submission.submissionId = :submissionId")
    void deleteByAnswer_Submission_SubmissionId(@Param("submissionId") UUID submissionId);
}
