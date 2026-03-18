package agsfjope.backend.core.exceptions.exampaper;

/**
 * Exception thrown when the uploaded file cannot be parsed because its internal
 * directory structure does not match the expected exam paper format.
 *
 * <p>Expected structure inside the archive:</p>
 * <pre>
 *   &lt;root-folder&gt;/
 *     1/
 *       Q1.docx         ← question document
 *       tc1.txt         ← test cases
 *     2/
 *       Q2.docx
 *       tc2.txt
 *     ...
 * </pre>
 *
 * <p>Common causes:</p>
 * <ul>
 *   <li>Archive is empty or has no numbered sub-folders.</li>
 *   <li>A numbered folder is missing its {@code Q{n}.docx} or {@code tc{n}.txt} file.</li>
 *   <li>A {@code tc{n}.txt} file has no valid {@code INPUT:} / {@code OUTPUT:} pairs.</li>
 *   <li>The {@code Q{n}.docx} file does not contain a parseable score value.</li>
 * </ul>
 *
 * <p>Handled by {@code GlobalExceptionHandler} → HTTP <strong>400 Bad Request</strong>.</p>
 */
public class InvalidZipStructureException extends RuntimeException {

    /**
     * Creates the exception with a descriptive message explaining which structural
     * requirement was not met.
     *
     * @param message human-readable error message
     */
    public InvalidZipStructureException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and the underlying cause.
     *
     * @param message human-readable error message
     * @param cause   the original exception that triggered this validation failure
     */
    public InvalidZipStructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
