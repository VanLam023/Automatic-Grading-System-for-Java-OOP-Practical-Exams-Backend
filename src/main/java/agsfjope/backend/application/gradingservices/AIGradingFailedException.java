package agsfjope.backend.application.gradingservices;

/**
 * Signals that a submission must be marked as failed instead of being finalized
 * with a potentially incorrect grading result.
 *
 * <p>Typical reasons: AI response truncated, invalid JSON, score inconsistency,
 * or missing per-question AI review data.</p>
 */
public class AIGradingFailedException extends RuntimeException {

    public AIGradingFailedException(String message) {
        super(message);
    }

    public AIGradingFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
