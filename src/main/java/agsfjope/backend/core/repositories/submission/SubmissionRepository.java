package agsfjope.backend.core.repositories.submission;

import agsfjope.backend.core.entities.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
