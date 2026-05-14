package agsfjope.backend.application.dtos.responses.exampaper;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for returning the full details of an uploaded exam paper.
 *
 * <p>Returned by the upload, get, and delete endpoints.
 * Contains metadata about the uploaded file plus the parsed list of questions and their test cases.</p>
 */
@Data
@Builder
public class ExamPaperResponse {

    /** Unique identifier of the exam paper. */
    private UUID examPaperId;

    /** UUID of the block this exam paper belongs to (BR-09: 1 block = 1 paper). */
    private UUID blockId;

    /** Display name of the block (e.g., "Block 10" or "Block 3"). */
    private String blockName;

    /** Original file name of the uploaded archive (e.g., {@code PRO192_PE_FA25_071125.zip}). */
    private String fileName;

    /** Presigned URL to view/download the original exam paper file. */
    private String fileUrl;

    /** Mã đề thi do giảng viên nhập khi upload. Null nếu không điền. */
    private String examCode;

    /** File size in bytes. Used to display file size in the UI (e.g., "5.2 MB"). */
    private Long fileSizeBytes;

    /**
     * Total number of questions parsed from the uploaded archive.
     * Reflects the count of numbered sub-folders found in the archive root.
     */
    private Integer totalQuestions;

    /**
     * Total number of test cases across all questions.
     * Computed as the sum of all {@code INPUT:} / {@code OUTPUT:} pairs.
     */
    private Integer totalTestCases;

    /** Timestamp when this exam paper was uploaded. */
    private OffsetDateTime uploadedAt;

    /**
     * Ordered list of parsed questions. Each question contains its test cases.
     * Ordered by {@code questionNumber} ascending.
     */
    private List<QuestionResponse> questions;
}
