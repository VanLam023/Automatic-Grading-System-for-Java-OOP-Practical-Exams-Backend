package agsfjope.backend.core.exceptions.submission;

/**
 * Thrown when a student attempts to submit a file for a block whose parent exam
 * is not currently in the {@code ONGOING} state.
 *
 * <p>This enforces <strong>BR-14</strong>: submissions are only accepted while the exam
 * is actively running (ONGOING). Attempting to submit when the exam is UPCOMING or
 * COMPLETED results in this exception.</p>
 *
 * <p>Mapped to HTTP <strong>409 Conflict</strong> by {@code GlobalExceptionHandler}.</p>
 */
public class ExamNotOngoingException extends RuntimeException {

    public ExamNotOngoingException(String message) {
        super(message);
    }
}
