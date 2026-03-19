package agsfjope.backend.application.submissionservices;

import agsfjope.backend.application.dtos.responses.submission.SubmissionResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * Service for student submission management.
 *
 * <p>Business rules enforced:</p>
 * <ul>
 *   <li><strong>BR-14</strong> — Only accept submissions while the exam is ONGOING.</li>
 *   <li><strong>BR-15</strong> — Validate archive structure: {@code {n}/run/*.jar} + {@code {n}/src/*.java}.</li>
 *   <li><strong>BR-16</strong> — File size ≤ MAX_UPLOAD_SIZE_MB (from SystemConfig).</li>
 *   <li><strong>BR-17</strong> — Resubmit: overwrite old submission completely.</li>
 *   <li><strong>BR-18</strong> — 1 student = 1 active submission per block.</li>
 * </ul>
 */
public interface SubmissionService {

    /**
     * Submits (or resubmits) a student's exam archive for a given block.
     *
     * <p>Flow:
     * <ol>
     *   <li>Validate exam and block ownership.</li>
     *   <li>BR-14: Verify exam is ONGOING.</li>
     *   <li>Verify block has an exam paper (students cannot submit without questions).</li>
     *   <li>BR-16: Validate file size and extension (.zip or .rar).</li>
     *   <li>BR-17: If prior submission exists, delete it (Answers → Submission → MinIO).</li>
     *   <li>Upload archive to MinIO.</li>
     *   <li>Persist Submission entity (status=SUBMITTED).</li>
     *   <li>Parse archive and persist Answer entities.</li>
     * </ol>
     *
     * @param examId    the parent exam UUID
     * @param blockId   the block UUID the student is submitting for
     * @param studentId the authenticated student's user UUID
     * @param file      the uploaded .zip or .rar file
     * @return full submission response including per-question answer breakdown
     * @throws agsfjope.backend.core.exceptions.submission.ExamNotOngoingException if exam is not ONGOING
     * @throws IllegalStateException                                                 if file is invalid (size, extension)
     * @throws agsfjope.backend.core.exceptions.auth.NotFoundException               if exam/block not found
     */
    SubmissionResponse submit(UUID examId, UUID blockId, UUID studentId, MultipartFile file);

    /**
     * Returns the current submission for the given student and block.
     *
     * @param examId    the parent exam UUID
     * @param blockId   the block UUID
     * @param studentId the authenticated student's user UUID
     * @return submission metadata and answer breakdown
     * @throws agsfjope.backend.core.exceptions.auth.NotFoundException if no submission found
     */
    SubmissionResponse getMySubmission(UUID examId, UUID blockId, UUID studentId);

    /**
     * Returns the raw archive stream of the student's submission for download.
     *
     * @param examId    the parent exam UUID
     * @param blockId   the block UUID
     * @param studentId the authenticated student's user UUID
     * @return raw InputStream of the archive file
     * @throws agsfjope.backend.core.exceptions.auth.NotFoundException if no submission found
     */
    InputStream downloadMySubmission(UUID examId, UUID blockId, UUID studentId);
}
