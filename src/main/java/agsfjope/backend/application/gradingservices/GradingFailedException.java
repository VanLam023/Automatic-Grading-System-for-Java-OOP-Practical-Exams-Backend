package agsfjope.backend.application.gradingservices;

/**
 * Thrown when the grading pipeline encounters a system-level error that
 * prevents a submission from being graded at all.
 *
 * <p>Unlike normal exceptions (which reset the submission to SUBMITTED),
 * this exception signals that the submission should be marked
 * {@code GRADING_FAILED} so staff can identify and investigate the root cause.</p>
 *
 * <h3>Triggers:</h3>
 * <ul>
 *   <li>Exam paper not found for the block</li>
 *   <li>GradingModeConfig not found</li>
 *   <li>Database connection exhausted</li>
 *   <li>Corrupt archive (zip/rar) — cannot extract</li>
 *   <li>Disk space / temp directory error</li>
 *   <li>URLClassLoader / UnsupportedClassVersionError (Java version mismatch)</li>
 *   <li>Thread interrupted</li>
 * </ul>
 */
public class GradingFailedException extends RuntimeException {

    public GradingFailedException(String message) {
        super(message);
    }

    public GradingFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
