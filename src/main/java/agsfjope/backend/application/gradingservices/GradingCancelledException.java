package agsfjope.backend.application.gradingservices;

/**
 * Thrown by {@link GradingPipelineService} when a stop signal is detected
 * mid-grading (between answers). Caught by {@link GradingService} to break out
 * of the submission loop cleanly.
 *
 * <p>This is an internal control-flow exception — never returned to the API client.</p>
 */
public class GradingCancelledException extends RuntimeException {
    public GradingCancelledException(String message) {
        super(message);
    }
}
