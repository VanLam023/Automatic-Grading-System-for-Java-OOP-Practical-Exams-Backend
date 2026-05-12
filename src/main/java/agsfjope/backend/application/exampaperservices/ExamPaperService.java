package agsfjope.backend.application.exampaperservices;

import agsfjope.backend.application.dtos.responses.exampaper.ExamPaperResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * Service interface for ExamPaper management.
 *
 * <p>An ExamPaper is the parsed representation of an exam archive (.zip or .rar)
 * uploaded by Exam Staff. Each Block can have exactly one ExamPaper (BR-09).</p>
 *
 * <p>Business rules enforced by implementations:</p>
 * <ul>
 *   <li><strong>BR-09</strong>: 1 Block = 1 ExamPaper. Uploading again auto-overwrites the old paper.</li>
 *   <li><strong>BR-10</strong>: Archive is parsed automatically → creates Questions and TestCases.</li>
 *   <li><strong>BR-11</strong>: Cannot modify or delete the paper once any student has submitted for that block.</li>
 *   <li><strong>BR-16</strong>: Uploaded file size must not exceed 20 MB.</li>
 * </ul>
 */
public interface ExamPaperService {

    /**
     * Uploads and parses an exam paper archive (.zip or .rar) for the given block.
     *
     * <p>Processing steps:</p>
     * <ol>
     *   <li>Validate block exists and belongs to the given exam.</li>
     *   <li>Enforce BR-11: reject if any student has already submitted for this block.</li>
     *   <li>Enforce BR-16: reject if file size exceeds 20 MB.</li>
     *   <li>Enforce BR-09 overwrite: if an old paper exists, delete its Questions, TestCases,
     *       DB record, and MinIO file before proceeding.</li>
     *   <li>Upload the raw archive to MinIO (bucket {@code exam-papers}).</li>
     *   <li>Parse the archive structure to extract Questions and TestCases.</li>
     *   <li>Persist ExamPaper, Questions, and TestCases to the database.</li>
     * </ol>
     *
     * @param examId  exam identifier (used to validate block ownership)
     * @param blockId block identifier (must belong to the given exam)
     * @param staffId UUID of the Exam Staff performing the upload
     * @param file    the uploaded archive file (must be .zip or .rar, max 20 MB)
     * @param examCode optional exam code entered by staff (e.g. "PRO192_PE_FA25"), may be null
     * @return full exam paper response including parsed questions and test cases
     * @throws agsfjope.backend.core.exceptions.auth.NotFoundException if exam or block not found
     * @throws IllegalArgumentException if block does not belong to the exam
     * @throws agsfjope.backend.core.exceptions.exampaper.ExamPaperHasSubmissionsException if BR-11 is violated
     * @throws agsfjope.backend.core.exceptions.exampaper.InvalidZipStructureException if archive cannot be parsed
     * @throws IllegalStateException if file type is not .zip or .rar, or size exceeds limit
     */
    ExamPaperResponse upload(UUID examId, UUID blockId, UUID staffId, MultipartFile file, String examCode);

    /**
     * Returns the exam paper metadata and all parsed questions/test cases for the given block.
     *
     * @param examId  exam identifier (for ownership validation)
     * @param blockId block identifier
     * @return full exam paper response
     * @throws agsfjope.backend.core.exceptions.auth.NotFoundException if no exam paper exists for the block
     * @throws IllegalArgumentException if block does not belong to the exam
     */
    ExamPaperResponse getByBlock(UUID examId, UUID blockId);

    /**
     * Deletes the exam paper (archive from MinIO + Questions + TestCases + DB record) for the given block.
     *
     * <p>Enforces BR-11: deletion is rejected if any student has already submitted for this block.</p>
     *
     * @param examId  exam identifier (for ownership validation)
     * @param blockId block identifier
     * @throws agsfjope.backend.core.exceptions.auth.NotFoundException if no exam paper exists for the block
     * @throws IllegalArgumentException if block does not belong to the exam
     * @throws agsfjope.backend.core.exceptions.exampaper.ExamPaperHasSubmissionsException if BR-11 is violated
     */
    void deleteByBlock(UUID examId, UUID blockId);

    /**
     * Downloads the original exam paper archive from MinIO storage.
     *
     * <p>The caller is responsible for closing the returned {@link InputStream}.</p>
     *
     * @param examId  exam identifier (for ownership validation)
     * @param blockId block identifier
     * @return raw input stream of the archive file
     * @throws agsfjope.backend.core.exceptions.auth.NotFoundException if no exam paper exists for the block
     * @throws IllegalArgumentException if block does not belong to the exam
     */
    InputStream downloadByBlock(UUID examId, UUID blockId);
}
