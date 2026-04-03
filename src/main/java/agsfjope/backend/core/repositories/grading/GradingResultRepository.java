package agsfjope.backend.core.repositories.grading;

import agsfjope.backend.core.entities.GradingResult;
import agsfjope.backend.core.repositories.grading.projections.ScoreBucketCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    // ─── Staff Dashboard — Grade Distribution ────────────────────────────────

    /**
     * Counts grading results whose totalScore falls within [minScore, maxScore).
     * The upper bound is exclusive except for the topmost bucket (9-10) which is inclusive.
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM GradingResults gr
            WHERE gr.TotalScore >= :minScore
              AND gr.TotalScore < :maxScore
            """,
            nativeQuery = true)
    long countByScoreRange(@Param("minScore") BigDecimal minScore,
                           @Param("maxScore") BigDecimal maxScore);

    /**
     * Counts grading results whose totalScore falls within [minScore, maxScore)
     * and belong to a submission in the given semester.
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM GradingResults gr
            JOIN Submissions s ON gr.SubmissionID = s.SubmissionID
            JOIN Blocks b ON s.BlockID = b.BlockID
            JOIN Exams e ON b.ExamID = e.ExamID
            WHERE gr.TotalScore >= :minScore
              AND gr.TotalScore < :maxScore
              AND e.Semester = :semester
            """,
            nativeQuery = true)
    long countByScoreRangeAndSemester(@Param("minScore") BigDecimal minScore,
                                      @Param("maxScore") BigDecimal maxScore,
                                      @Param("semester") String semester);

    /**
     * Counts all grading results (total graded submissions).
     */
    @Query("SELECT COUNT(gr) FROM GradingResult gr")
    long countAll();

    /**
     * Counts all grading results for submissions in the given semester.
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM GradingResults gr
            JOIN Submissions s ON gr.SubmissionID = s.SubmissionID
            JOIN Blocks b ON s.BlockID = b.BlockID
            JOIN Exams e ON b.ExamID = e.ExamID
            WHERE e.Semester = :semester
            """,
            nativeQuery = true)
    long countAllBySemester(@Param("semester") String semester);

    /**
     * Aggregates score buckets in one query for dashboard chart.
     * Buckets are kept as agreed:
     * 0-4, 4-6, 6-8, 8-9, 9-10
     */
    @Query(value = """
            SELECT
                CASE
                    WHEN gr.TotalScore >= 0 AND gr.TotalScore < 4 THEN '0-4'
                    WHEN gr.TotalScore >= 4 AND gr.TotalScore < 6 THEN '4-6'
                    WHEN gr.TotalScore >= 6 AND gr.TotalScore < 8 THEN '6-8'
                    WHEN gr.TotalScore >= 8 AND gr.TotalScore < 9 THEN '8-9'
                    WHEN gr.TotalScore >= 9 AND gr.TotalScore <= 10 THEN '9-10'
                END AS bucketLabel,
                COUNT(*) AS bucketCount
            FROM GradingResults gr
            WHERE gr.TotalScore IS NOT NULL
            GROUP BY
                CASE
                    WHEN gr.TotalScore >= 0 AND gr.TotalScore < 4 THEN '0-4'
                    WHEN gr.TotalScore >= 4 AND gr.TotalScore < 6 THEN '4-6'
                    WHEN gr.TotalScore >= 6 AND gr.TotalScore < 8 THEN '6-8'
                    WHEN gr.TotalScore >= 8 AND gr.TotalScore < 9 THEN '8-9'
                    WHEN gr.TotalScore >= 9 AND gr.TotalScore <= 10 THEN '9-10'
                END
            """,
            nativeQuery = true)
    List<ScoreBucketCountProjection> aggregateScoreBuckets();

    /**
     * Aggregates score buckets in one query for dashboard chart filtered by semester.
     * Buckets are kept as agreed:
     * 0-4, 4-6, 6-8, 8-9, 9-10
     */
    @Query(value = """
            SELECT
                CASE
                    WHEN gr.TotalScore >= 0 AND gr.TotalScore < 4 THEN '0-4'
                    WHEN gr.TotalScore >= 4 AND gr.TotalScore < 6 THEN '4-6'
                    WHEN gr.TotalScore >= 6 AND gr.TotalScore < 8 THEN '6-8'
                    WHEN gr.TotalScore >= 8 AND gr.TotalScore < 9 THEN '8-9'
                    WHEN gr.TotalScore >= 9 AND gr.TotalScore <= 10 THEN '9-10'
                END AS bucketLabel,
                COUNT(*) AS bucketCount
            FROM GradingResults gr
            JOIN Submissions s ON gr.SubmissionID = s.SubmissionID
            JOIN Blocks b ON s.BlockID = b.BlockID
            JOIN Exams e ON b.ExamID = e.ExamID
            WHERE gr.TotalScore IS NOT NULL
              AND e.Semester = :semester
            GROUP BY
                CASE
                    WHEN gr.TotalScore >= 0 AND gr.TotalScore < 4 THEN '0-4'
                    WHEN gr.TotalScore >= 4 AND gr.TotalScore < 6 THEN '4-6'
                    WHEN gr.TotalScore >= 6 AND gr.TotalScore < 8 THEN '6-8'
                    WHEN gr.TotalScore >= 8 AND gr.TotalScore < 9 THEN '8-9'
                    WHEN gr.TotalScore >= 9 AND gr.TotalScore <= 10 THEN '9-10'
                END
            """,
            nativeQuery = true)
    List<ScoreBucketCountProjection> aggregateScoreBucketsBySemester(@Param("semester") String semester);

    // ─── Exam Statistics — Block-level aggregation (PROC-006) ────────────────

    /**
     * Counts graded submissions in a specific block.
     *
     * @param blockId the block UUID
     * @return number of grading results for this block
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM GradingResults gr
            JOIN Submissions s ON gr.SubmissionID = s.SubmissionID
            WHERE s.BlockID = :blockId
            """,
            nativeQuery = true)
    long countGradedByBlockId(@Param("blockId") UUID blockId);

    /**
     * Calculates average total score for a block.
     *
     * @param blockId the block UUID
     * @return average score, or null if no results
     */
    @Query(value = """
            SELECT AVG(gr.TotalScore)
            FROM GradingResults gr
            JOIN Submissions s ON gr.SubmissionID = s.SubmissionID
            WHERE s.BlockID = :blockId
            """,
            nativeQuery = true)
    Double avgScoreByBlockId(@Param("blockId") UUID blockId);

    /**
     * Finds the highest total score in a block.
     *
     * @param blockId the block UUID
     * @return max score, or null if no results
     */
    @Query(value = """
            SELECT MAX(gr.TotalScore)
            FROM GradingResults gr
            JOIN Submissions s ON gr.SubmissionID = s.SubmissionID
            WHERE s.BlockID = :blockId
            """,
            nativeQuery = true)
    Double maxScoreByBlockId(@Param("blockId") UUID blockId);

    /**
     * Finds the lowest total score in a block.
     *
     * @param blockId the block UUID
     * @return min score, or null if no results
     */
    @Query(value = """
            SELECT MIN(gr.TotalScore)
            FROM GradingResults gr
            JOIN Submissions s ON gr.SubmissionID = s.SubmissionID
            WHERE s.BlockID = :blockId
            """,
            nativeQuery = true)
    Double minScoreByBlockId(@Param("blockId") UUID blockId);

    /**
     * Counts PASS results in a block.
     *
     * @param blockId the block UUID
     * @return number of PASS results
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM GradingResults gr
            JOIN Submissions s ON gr.SubmissionID = s.SubmissionID
            WHERE s.BlockID = :blockId
              AND gr.Status = CAST('PASS' AS grading_result_status)
            """,
            nativeQuery = true)
    long countPassByBlockId(@Param("blockId") UUID blockId);

    /**
     * Counts FAIL results in a block.
     *
     * @param blockId the block UUID
     * @return number of FAIL results
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM GradingResults gr
            JOIN Submissions s ON gr.SubmissionID = s.SubmissionID
            WHERE s.BlockID = :blockId
              AND gr.Status = CAST('FAIL' AS grading_result_status)
            """,
            nativeQuery = true)
    long countFailByBlockId(@Param("blockId") UUID blockId);

    /**
     * Counts grading results whose totalScore falls within [minScore, maxScore)
     * for a specific block. Used to build score distribution histogram.
     *
     * @param blockId the block UUID
     * @param minScore lower bound (inclusive)
     * @param maxScore upper bound (exclusive)
     * @return count of matching grading results
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM GradingResults gr
            JOIN Submissions s ON gr.SubmissionID = s.SubmissionID
            WHERE s.BlockID = :blockId
              AND gr.TotalScore >= :minScore
              AND gr.TotalScore < :maxScore
            """,
            nativeQuery = true)
    long countByBlockIdAndScoreRange(@Param("blockId") UUID blockId,
                                     @Param("minScore") BigDecimal minScore,
                                     @Param("maxScore") BigDecimal maxScore);
}