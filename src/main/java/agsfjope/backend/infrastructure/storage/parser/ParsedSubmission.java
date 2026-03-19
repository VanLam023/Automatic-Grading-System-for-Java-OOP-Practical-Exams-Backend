package agsfjope.backend.infrastructure.storage.parser;

import java.util.List;

/**
 * Immutable result of parsing a student's submission archive (.zip or .rar).
 *
 * <p>The archive is structured as numbered question folders (1/, 2/, 3/, ...),
 * each containing a {@code run/} sub-folder (compiled .jar) and a {@code src/}
 * sub-folder (Java source files).</p>
 *
 * <p>Parsing is lenient:
 * <ul>
 *   <li>Extra files/folders (e.g., build/, nbproject/) are silently ignored.</li>
 *   <li>Missing jar → {@code jarEntryPath = null}, question gets 0 test-case score.</li>
 *   <li>Missing question folder → outer service creates a null Answer for that question.</li>
 * </ul></p>
 */
public record ParsedSubmission(List<ParsedAnswer> answers) {

    /**
     * Parsed answer for one question.
     *
     * @param questionNumber 1-based question number (matches folder name)
     * @param jarEntryPath   relative path in archive to the .jar file, or {@code null} if missing
     * @param sourceEntryPaths relative paths to all .java files inside {@code src/},
     *                         empty list if no source found
     */
    public record ParsedAnswer(
            int questionNumber,
            String jarEntryPath,
            List<String> sourceEntryPaths
    ) {
        /** @return true if this answer has a compiled .jar */
        public boolean hasJar() { return jarEntryPath != null; }

        /** @return true if this answer has at least one source file */
        public boolean hasSource() { return !sourceEntryPaths.isEmpty(); }
    }
}
