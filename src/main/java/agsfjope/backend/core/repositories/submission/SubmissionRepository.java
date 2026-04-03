package agsfjope.backend.core.repositories.submission;

import agsfjope.backend.core.entities.Submission;
import agsfjope.backend.core.enums.SubmissionStatus;
import agsfjope.backend.core.repositories.submission.projections.SubmissionListRowProjection;
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

    boolean existsByBlock_BlockId(UUID blockId);

    Optional<Submission> findByStudent_UserIdAndBlock_BlockId(UUID studentId, UUID blockId);

    List<Submission> findByBlock_BlockIdAndStatus(UUID blockId, SubmissionStatus status);

    List<Submission> findByBlock_BlockIdAndStatusIn(UUID blockId, List<SubmissionStatus> statuses);

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.block.blockId = :blockId")
    long countByBlock_BlockId(@Param("blockId") UUID blockId);

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.block.blockId = :blockId AND s.status = :status")
    long countByBlock_BlockIdAndStatus(@Param("blockId") UUID blockId,
                                       @Param("status") SubmissionStatus status);

    List<Submission> findAllByBlock_BlockIdOrderBySubmittedAtDesc(UUID blockId);

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
     * Optimized flat query for Exam Staff submission list page.
     * Pushes search/filter/pagination to DB and joins GradingResults in one query.
     */
    @Query(value = """
            SELECT
                s.SubmissionID    AS submissionId,
                s.FileName        AS fileName,
                s.FileSizeBytes   AS fileSizeBytes,
                s.Status          AS submissionStatus,
                s.SubmittedAt     AS submittedAt,
                st.UserID         AS studentId,
                st.FullName       AS studentName,
                st.MSSV           AS studentCode,
                st.Email          AS studentEmail,
                gr.GradingResultID AS gradingResultId,
                gr.Status         AS gradingStatus,
                gr.TotalScore     AS totalScore,
                gr.MaxScore       AS maxScore,
                gr.UpdatedAt      AS gradedAt
            FROM Submissions s
            JOIN Blocks b ON s.BlockID = b.BlockID
            JOIN Users st ON s.StudentID = st.UserID
            LEFT JOIN GradingResults gr ON gr.SubmissionID = s.SubmissionID
            WHERE s.BlockID = :blockId
              AND b.ExamID = :examId
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(st.FullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(st.MSSV)     LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY s.SubmittedAt DESC, s.SubmissionID DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM Submissions s
            JOIN Blocks b ON s.BlockID = b.BlockID
            JOIN Users st ON s.StudentID = st.UserID
            WHERE s.BlockID = :blockId
              AND b.ExamID = :examId
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(st.FullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(st.MSSV)     LIKE LOWER(CONCAT('%', :keyword, '%')))
            """,
            nativeQuery = true)
    Page<SubmissionListRowProjection> findSubmissionListPageByBlock(
            @Param("examId") UUID examId,
            @Param("blockId") UUID blockId,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * Optimized flat query for Exam Staff submission list page with status filter.
     */
    @Query(value = """
            SELECT
                s.SubmissionID    AS submissionId,
                s.FileName        AS fileName,
                s.FileSizeBytes   AS fileSizeBytes,
                s.Status          AS submissionStatus,
                s.SubmittedAt     AS submittedAt,
                st.UserID         AS studentId,
                st.FullName       AS studentName,
                st.MSSV           AS studentCode,
                st.Email          AS studentEmail,
                gr.GradingResultID AS gradingResultId,
                gr.Status         AS gradingStatus,
                gr.TotalScore     AS totalScore,
                gr.MaxScore       AS maxScore,
                gr.UpdatedAt      AS gradedAt
            FROM Submissions s
            JOIN Blocks b ON s.BlockID = b.BlockID
            JOIN Users st ON s.StudentID = st.UserID
            LEFT JOIN GradingResults gr ON gr.SubmissionID = s.SubmissionID
            WHERE s.BlockID = :blockId
              AND b.ExamID = :examId
              AND s.Status = CAST(:status AS submission_status)
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(st.FullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(st.MSSV)     LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY s.SubmittedAt DESC, s.SubmissionID DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM Submissions s
            JOIN Blocks b ON s.BlockID = b.BlockID
            JOIN Users st ON s.StudentID = st.UserID
            WHERE s.BlockID = :blockId
              AND b.ExamID = :examId
              AND s.Status = CAST(:status AS submission_status)
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(st.FullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(st.MSSV)     LIKE LOWER(CONCAT('%', :keyword, '%')))
            """,
            nativeQuery = true)
    Page<SubmissionListRowProjection> findSubmissionListPageByBlockAndStatus(
            @Param("examId") UUID examId,
            @Param("blockId") UUID blockId,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);

    long countBySubmittedAtBetween(OffsetDateTime from, OffsetDateTime to);

    long countByStatus(SubmissionStatus status);

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.block.exam.semester = :semester")
    long countBySemester(@Param("semester") String semester);

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.status = :status AND s.block.exam.semester = :semester")
    long countByStatusAndSemester(@Param("status") SubmissionStatus status,
                                  @Param("semester") String semester);
}