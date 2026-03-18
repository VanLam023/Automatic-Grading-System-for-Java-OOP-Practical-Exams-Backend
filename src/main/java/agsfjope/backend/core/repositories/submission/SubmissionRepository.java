package agsfjope.backend.core.repositories.submission;

import agsfjope.backend.core.entities.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for {@link Submission} entity.
 *
 * <p>Currently provides the minimum queries needed to enforce business rules
 * around exam paper management. Additional queries for the full submission flow
 * will be added in a later feature phase.</p>
 */
@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    /**
     * Checks whether any student has already submitted a file for the given block.
     *
     * <p>Used to enforce <strong>BR-11</strong>: an exam paper cannot be modified or deleted
     * once at least one submission exists for its block. This prevents changing the exam
     * questions after students have already started submitting answers.</p>
     *
     * @param blockId the block's UUID to check
     * @return true if at least one submission exists for this block
     */
    boolean existsByBlock_BlockId(UUID blockId);
}
