package agsfjope.backend.core.repositories.submission;

import agsfjope.backend.core.entities.Submission;
import agsfjope.backend.core.enums.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Submission} entity.
 */
@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    /**
     * Checks whether any student has already submitted a file for the given block.
     *
     * <p>Used to enforce <strong>BR-11</strong>: an exam paper cannot be modified or deleted
     * once at least one submission exists for its block.</p>
     *
     * @param blockId the block's UUID to check
     * @return true if at least one submission exists for this block
     */
    boolean existsByBlock_BlockId(UUID blockId);

    /**
     * Finds the existing submission (if any) for a specific student and block.
     *
     * <p>Used to detect resubmit (BR-17): if a submission already exists,
     * the old one must be fully deleted before the new one is saved.</p>
     *
     * @param studentId the student's user UUID
     * @param blockId   the block UUID
     * @return the existing submission, or empty if none
     */
    Optional<Submission> findByStudent_UserIdAndBlock_BlockId(UUID studentId, UUID blockId);

    /**
     * Finds all submissions for a block with a specific status.
     * Used by GradingService to fetch submissions that need to be graded (SUBMITTED status).
     *
     * @param blockId the block UUID
     * @param status  the submission status filter
     * @return list of matching submissions
     */
    List<Submission> findByBlock_BlockIdAndStatus(UUID blockId, SubmissionStatus status);

    /**
     * Finds submissions for a block matching any of the given statuses.
     * Used by GradingService to include stuck-GRADING submissions in GRADE_ALL.
     */
    List<Submission> findByBlock_BlockIdAndStatusIn(UUID blockId, List<SubmissionStatus> statuses);


    /**
     * Counts total submissions for a block.
     * Uses explicit JPQL to avoid unnecessary JOIN with Blocks table.
     */
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.block.blockId = :blockId")
    long countByBlock_BlockId(@Param("blockId") UUID blockId);

    /**
     * Counts submissions for a block with a specific status.
     * Uses explicit JPQL to avoid unnecessary JOIN with Blocks table.
     */
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.block.blockId = :blockId AND s.status = :status")
    long countByBlock_BlockIdAndStatus(@Param("blockId") UUID blockId,
                                       @Param("status") SubmissionStatus status);

    /**
     * Finds ALL submissions for a block (any status).
     * Used by EXAM_STAFF to display the full submission list.
     *
     * @param blockId the block UUID
     * @return list of all submissions in the block, ordered by submission time desc
     */
    List<Submission> findAllByBlock_BlockIdOrderBySubmittedAtDesc(UUID blockId);

    /**
     * Pageable search — no status filter, keyword only (or all if keyword blank).
     */
    @Query(value = """
            SELECT s FROM Submission s
            JOIN FETCH s.student st
            WHERE s.block.blockId = :blockId
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(st.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(st.mssv)     LIKE LOWER(CONCAT('%', :keyword, '%')))
            """,
            countQuery = """
            SELECT COUNT(s) FROM Submission s
            JOIN s.student st
            WHERE s.block.blockId = :blockId
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(st.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(st.mssv)     LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Submission> findPageByBlock(
            @Param("blockId") UUID blockId,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * Pageable search — filtered by a specific status + optional keyword.
     */
    @Query(value = """
            SELECT s FROM Submission s
            JOIN FETCH s.student st
            WHERE s.block.blockId = :blockId
              AND s.status = :status
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(st.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(st.mssv)     LIKE LOWER(CONCAT('%', :keyword, '%')))
            """,
            countQuery = """
            SELECT COUNT(s) FROM Submission s
            JOIN s.student st
            WHERE s.block.blockId = :blockId
              AND s.status = :status
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(st.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(st.mssv)     LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Submission> findPageByBlockAndStatus(
            @Param("blockId") UUID blockId,
            @Param("status")  SubmissionStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * Counts submissions submitted within the given date range.
     * Used by the Admin Dashboard date-filtered overview.
     *
     * @param from start of date range (inclusive)
     * @param to   end of date range (inclusive)
     * @return number of submissions with submittedAt between from and to
     */
    long countBySubmittedAtBetween(OffsetDateTime from, OffsetDateTime to);
}
