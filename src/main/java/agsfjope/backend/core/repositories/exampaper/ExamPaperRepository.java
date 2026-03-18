package agsfjope.backend.core.repositories.exampaper;

import agsfjope.backend.core.entities.ExamPaper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link ExamPaper} entity.
 *
 * <p>Business rules enforced through this repository:</p>
 * <ul>
 *   <li>BR-09: 1 Block = 1 ExamPaper. {@code existsByBlock_BlockId} is used to check before upload.</li>
 * </ul>
 */
@Repository
public interface ExamPaperRepository extends JpaRepository<ExamPaper, UUID> {

    /**
     * Finds the exam paper associated with the given block.
     * Returns empty if the block has no exam paper yet.
     *
     * @param blockId the block's UUID
     * @return the exam paper for that block, or empty
     */
    Optional<ExamPaper> findByBlock_BlockId(UUID blockId);

    /**
     * Checks whether the given block already has an exam paper uploaded.
     * Used in upload logic: if true, the old paper must be replaced (BR-09 auto-overwrite).
     *
     * @param blockId the block's UUID
     * @return true if an exam paper already exists for this block
     */
    boolean existsByBlock_BlockId(UUID blockId);
}
