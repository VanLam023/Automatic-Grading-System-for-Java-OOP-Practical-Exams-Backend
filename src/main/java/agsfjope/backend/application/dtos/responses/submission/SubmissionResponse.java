package agsfjope.backend.application.dtos.responses.submission;

import agsfjope.backend.core.enums.SubmissionStatus;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO returned after a student submits or re-submits their exam file.
 *
 * <p>Includes metadata about the submission plus a breakdown of parsed answers
 * (one per question defined in the exam paper). Answers for questions the student
 * did not include will be present with {@code hasJar = false} and {@code hasSource = false}.</p>
 */
@Value
@Builder
public class SubmissionResponse {

    /** Unique identifier of the submission. */
    UUID submissionId;

    /** Block this submission belongs to. */
    UUID blockId;

    /** Human-readable block name (e.g., "Block 10"). */
    String blockName;

    /** Name of the parent exam. */
    String examName;

    /** Original filename of the uploaded archive. */
    String fileName;

    /** File size in bytes. */
    long fileSizeBytes;

    /** Current status of the submission: SUBMITTED / GRADING / GRADED. */
    SubmissionStatus status;

    /** Timestamp when the submission was (last) submitted. */
    OffsetDateTime submittedAt;

    /** Number of answers parsed from the archive. */
    int totalAnswers;

    /**
     * Whether this was a resubmission.
     * {@code true} if a previous submission existed and was replaced (BR-17).
     */
    boolean resubmit;

    /** Per-question answer breakdown. */
    List<AnswerResponse> answers;
}
