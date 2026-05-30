package agsfjope.backend.core.repositories.block;

import agsfjope.backend.core.entities.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Block entity.
 * Blocks are fixed per exam (Block 10 + Block 3 — BR-08).
 */
@Repository
public interface BlockRepository extends JpaRepository<Block, UUID> {

    /** Returns all blocks belonging to the given exam, ordered by name. */
    List<Block> findByExam_ExamIdOrderByNameAsc(UUID examId);

    /** Finds a specific block by its ID. */
    Optional<Block> findByBlockId(UUID blockId);

    /** Checks whether the given exam already has blocks (to avoid duplicates). */
    boolean existsByExam_ExamId(UUID examId);

    /**
     * Checks if any block of the given exam has a StartTime within the next 14 days
     * (i.e., startTime <= threshold where threshold = now + 14 days).
     * Used in deleteExam: cannot delete if any block is starting soon or has started.
     *
     * @param examId    exam identifier
     * @param threshold cutoff = now + 14 days
     * @return true if at least one block starts on or before the threshold
     */
    @Query("SELECT COUNT(b) > 0 FROM Block b WHERE b.exam.examId = :examId AND b.startTime <= :threshold")
    boolean existsBlockStartingOnOrBefore(@Param("examId") UUID examId, @Param("threshold") java.time.OffsetDateTime threshold);

    /**
     * Checks if a block belongs to a specific exam.
     * Used by Exam Statistics to validate that blockId is not from a different exam.
     *
     * @param blockId the block UUID
     * @param examId  the exam UUID to validate ownership
     * @return true if the block exists and belongs to the given exam
     */
    boolean existsByBlockIdAndExam_ExamId(UUID blockId, UUID examId);

    /**
     * Finds a block and eagerly loads its associated Exam (JOIN FETCH).
     *
     * <p>Use this method when you need to access block.getExam() outside of an active
     * transaction (e.g., in a Controller), to avoid LazyInitializationException.</p>
     *
     * @param blockId the block UUID
     * @return Optional Block with Exam eagerly loaded
     */
    @Query("SELECT b FROM Block b JOIN FETCH b.exam WHERE b.blockId = :blockId")
    Optional<Block> findByBlockIdWithExam(@Param("blockId") UUID blockId);

    /**
     * Tìm tất cả các Block có thời gian bắt đầu trong một khoảng thời gian nhất định (window).
     * Dùng cho Scheduler để nhắc nhở kỳ thi sắp mở.
     */
    List<Block> findByStartTimeBetween(java.time.OffsetDateTime after, java.time.OffsetDateTime before);

    /** Kiểm tra tên block đã tồn tại trong exam chưa (unique constraint ExamID + Name). */
    boolean existsByExam_ExamIdAndName(UUID examId, String name);

    /**
     * Lấy toàn bộ Block kèm Exam (JOIN FETCH để tránh N+1 / LazyInitializationException).
     * Dùng cho endpoint GET /api/staff/blocks — trả về danh sách Block + Exam trong 1 lần.
     * Sắp xếp: Semester DESC → Tên Exam ASC → Tên Block ASC.
     *
     * @return danh sách Block với Exam đã load sẵn
     */
    @Query("SELECT b FROM Block b JOIN FETCH b.exam e WHERE e.deletedAt IS NULL " +
           "ORDER BY e.semester DESC, e.name ASC, b.name ASC")
    List<Block> findAllWithExamOrderBySemesterDesc();


    /**
     * Tìm các block khác trong cùng kỳ thi có khoảng thời gian bị chồng với khoảng thời gian đang chọn.
     * Điều kiện overlap: newStart < existingEnd && newEnd > existingStart.
     */
    @Query("""
           SELECT b FROM Block b
           WHERE b.exam.examId = :examId
             AND b.blockId <> :blockId
             AND b.startTime IS NOT NULL
             AND b.endTime IS NOT NULL
             AND :startTime < b.endTime
             AND :endTime > b.startTime
           ORDER BY b.startTime ASC
           """)
    List<Block> findOverlappingBlocksInExam(@Param("examId") UUID examId,
                                            @Param("blockId") UUID blockId,
                                            @Param("startTime") java.time.OffsetDateTime startTime,
                                            @Param("endTime") java.time.OffsetDateTime endTime);

}
