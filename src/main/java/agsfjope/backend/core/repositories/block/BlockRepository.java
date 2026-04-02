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
}
