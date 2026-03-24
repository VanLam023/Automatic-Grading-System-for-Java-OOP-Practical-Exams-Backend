package agsfjope.backend.core.exceptions.exam;

/**
 * Exception thrown when an exam-related conflict occurs.
 * For example, attempting to delete an exam that already has submissions (BR-12).
 * Handled by {@code GlobalExceptionHandler} → HTTP 409 Conflict.
 */
public class ExamConflictException extends RuntimeException {

    public ExamConflictException(String message) {
        super(message);
    }
}
