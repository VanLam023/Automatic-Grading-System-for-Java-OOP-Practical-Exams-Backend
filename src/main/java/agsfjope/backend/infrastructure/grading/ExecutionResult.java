package agsfjope.backend.infrastructure.grading;

/**
 * Result of running a student JAR against a single test case input.
 * All fields are immutable — populated by {@link JarSandboxExecutor}.
 */
public record ExecutionResult(
        /** Standard output captured from the student JAR. */
        String stdout,

        /** Standard error captured from the student JAR. */
        String stderr,

        /** Process exit code. 0 = normal, non-zero = error. */
        int exitCode,

        /** Execution time in milliseconds. */
        long executionTimeMs,

        /** True if the process was killed due to timeout. */
        boolean timeout,

        /**
         * True if tampered class files were detected (checksum mismatch).
         * When true, the entire answer receives 0 score.
         */
        boolean tamperedFiles,

        /**
         * Human-readable error or violation description.
         * Shown to the student in their result view.
         */
        String errorMessage
) {
    /** Convenience factory — success result. */
    public static ExecutionResult success(String stdout, long execTimeMs) {
        return new ExecutionResult(stdout, "", 0, execTimeMs, false, false, null);
    }

    /** Convenience factory — timeout result. */
    public static ExecutionResult timeout(long timeLimitMs) {
        return new ExecutionResult("", "", -1, timeLimitMs, true, false,
                "TIMEOUT: exceeded " + timeLimitMs + "ms time limit");
    }

    /** Convenience factory — runtime error result. */
    public static ExecutionResult runtimeError(String stderr, long execTimeMs, int exitCode) {
        String msg = "RUNTIME ERROR (exit " + exitCode + "): "
                + (stderr.isBlank() ? "No details available" : stderr.trim());
        return new ExecutionResult("", stderr, exitCode, execTimeMs, false, false, msg);
    }

    /** Convenience factory — tampered exam files detected. */
    public static ExecutionResult tamperedExamFiles(String details) {
        return new ExecutionResult("", "", -1, 0, false, true,
                "VIOLATION: Modified exam class files detected — " + details);
    }
}
