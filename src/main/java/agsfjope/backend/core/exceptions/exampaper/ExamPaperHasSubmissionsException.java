package agsfjope.backend.core.exceptions.exampaper;

/**
 * Exception thrown when an attempt is made to modify or delete an exam paper
 * after at least one student has already submitted for that block.
 *
 * <p>Enforces <strong>BR-11</strong>: "Không được chỉnh sửa hoặc xóa đề thi sau khi đã có
 * ít nhất 1 sinh viên nộp bài cho block đó."</p>
 *
 * <p>Handled by {@code GlobalExceptionHandler} → HTTP <strong>409 Conflict</strong>.</p>
 */
public class ExamPaperHasSubmissionsException extends RuntimeException {

    /**
     * Creates the exception with a descriptive message.
     *
     * @param message error message describing why the operation was rejected
     */
    public ExamPaperHasSubmissionsException(String message) {
        super(message);
    }
}
