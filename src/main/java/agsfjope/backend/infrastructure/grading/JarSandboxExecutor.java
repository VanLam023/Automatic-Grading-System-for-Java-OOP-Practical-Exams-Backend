package agsfjope.backend.infrastructure.grading;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * Runs a student's .jar file against a single test case input using ProcessBuilder.
 *
 * <h3>Sandbox constraints:</h3>
 * <ul>
 *   <li>-Xmx128m: memory limit 128 MB</li>
 *   <li>-Xss1m: stack size 1 MB (prevents stack overflow attacks)</li>
 *   <li>-XX:+UseSerialGC: lightweight GC, avoids spawning extra GC threads</li>
 *   <li>Timeout: configurable per test case ({@code TestCase.timeLimitMs}), default 10s</li>
 *   <li>Checksum: verifies exam-provided .class files were not tampered with</li>
 * </ul>
 *
 * <h3>Error detection:</h3>
 * <ul>
 *   <li>Runtime error: any non-zero exit code → ERROR status</li>
 *   <li>Timeout: process survives waitFor() duration → kill + TIMEOUT status</li>
 *   <li>Tampered files: MD5 checksum mismatch on exam .class files → TAMPERED</li>
 *   <li>System.exit(): safe — kills only the child process, never the server</li>
 * </ul>
 */
@Slf4j
@Component
public class JarSandboxExecutor {

    private static final int DEFAULT_TIMEOUT_MS  = 10_000; // 10 seconds
    private static final int MAX_OUTPUT_BYTES    = 64 * 1024; // 64 KB max stdout

    /**
     * Path to the java executable used to run student JARs.
     * Configure via 'sandbox.java-executable' in application.yml or SANDBOX_JAVA env var.
     * Must point to a JDK >= Java version students used to compile (typically Java 21).
     */
    @Value("${sandbox.java-executable:java}")
    private String javaExecutable;

    // ─── PUBLIC API ──────────────────────────────────────────────────────────

    /**
     * Runs a single test case against the student JAR.
     *
     * @param studentJar     path to the student's .jar file
     * @param examClassDir   path to the directory containing exam-provided .class files
     * @param originalChecksums map of filename → MD5 checksum for exam .class files
     *                          (pre-computed when exam paper was uploaded)
     * @param inputData      test case input to feed via stdin
     * @param timeLimitMs    timeout in milliseconds (0 = use default 10s)
     * @return ExecutionResult capturing stdout, stderr, timing, and any errors
     */
    public ExecutionResult run(Path studentJar,
                               Path examClassDir,
                               Map<String, String> originalChecksums,
                               String inputData,
                               int timeLimitMs) {
        long timeout = timeLimitMs > 0 ? timeLimitMs : DEFAULT_TIMEOUT_MS;

        // Step 1: Verify exam .class files were not tampered with.
        // Compares the exam's reference checksums against the same-named .class files
        // found INSIDE the student JAR (student may have re-compiled / replaced them).
        if (originalChecksums != null && !originalChecksums.isEmpty()) {
            ExecutionResult tamperResult = verifyExamFiles(studentJar, originalChecksums);
            if (tamperResult != null) return tamperResult;
        }

        // Step 2: Build classpath: student.jar + exam classes directory
        String classpath = studentJar.toAbsolutePath() + File.pathSeparator
                + examClassDir.toAbsolutePath();

        // Step 3: Build ProcessBuilder command
        List<String> cmd = buildCommand(classpath, studentJar.getParent());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(studentJar.getParent().toFile());
        pb.redirectErrorStream(false); // keep stdout/stderr separate

        long startTime = System.currentTimeMillis();
        Process process = null;
        try {
            process = pb.start();

            // Step 4: Write input to stdin in a separate thread (avoid blocking)
            final Process p = process;
            Thread stdinWriter = new Thread(() -> {
                try (PrintWriter writer = new PrintWriter(
                        new BufferedWriter(new OutputStreamWriter(p.getOutputStream())))) {
                    if (inputData != null && !inputData.isBlank()) {
                        writer.print(inputData);
                    }
                } catch (Exception ignored) {}
            });
            stdinWriter.start();

            // Step 5: Capture stdout/stderr concurrently (prevent buffer deadlock)
            StringWriter stdoutCapture = new StringWriter();
            StringWriter stderrCapture = new StringWriter();

            Thread stdoutReader = captureStream(process.getInputStream(), stdoutCapture);
            Thread stderrReader = captureStream(process.getErrorStream(), stderrCapture);

            stdoutReader.start();
            stderrReader.start();

            // Step 6: Wait with timeout
            boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
            long execTimeMs = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                log.debug("Process killed (timeout {}ms)", timeout);
                return ExecutionResult.timeout(timeout);
            }

            stdinWriter.join(500);
            stdoutReader.join(1000);
            stderrReader.join(1000);

            int exitCode = process.exitValue();
            String stdout = truncate(stdoutCapture.toString(), MAX_OUTPUT_BYTES);
            String stderr = stderrCapture.toString();

            if (exitCode != 0) {
                return ExecutionResult.runtimeError(stderr, execTimeMs, exitCode);
            }

            return ExecutionResult.success(stdout, execTimeMs);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExecutionResult.runtimeError("Grading interrupted", 0, -1);
        } catch (IOException e) {
            log.error("Failed to start sandbox process: {}", e.getMessage());
            return ExecutionResult.runtimeError("Failed to launch JAR: " + e.getMessage(), 0, -1);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * Pre-computes MD5 checksums for all .class files in a directory.
     * Called when exam paper is uploaded — checksums stored for later verification.
     *
     * @param classDir directory containing exam .class files
     * @return map of filename → MD5 hex string
     */
    public Map<String, String> computeChecksums(Path classDir) throws IOException {
        Map<String, String> checksums = new LinkedHashMap<>();
        if (!Files.exists(classDir)) return checksums;

        try (var walk = Files.walk(classDir)) {
            walk.filter(p -> p.toString().endsWith(".class"))
                .forEach(p -> {
                    try {
                        String checksum = md5Hex(Files.readAllBytes(p));
                        checksums.put(p.getFileName().toString(), checksum);
                    } catch (IOException e) {
                        log.warn("Could not checksum {}: {}", p, e.getMessage());
                    }
                });
        }
        return checksums;
    }

    // ─── PRIVATE HELPERS ─────────────────────────────────────────────────────

    private List<String> buildCommand(String classpath, Path workDir) {
        log.debug("[SANDBOX] Using java executable: {}", javaExecutable);
        return List.of(
                javaExecutable,
                "-Xmx128m",                         // Memory limit 128 MB
                "-Xss1m",                            // Stack size 1 MB
                "-XX:+UseSerialGC",                  // Lightweight GC
                "-Djava.io.tmpdir=" + workDir,       // Sandbox temp dir
                "-Duser.language=en",                // Force Locale.US: Scanner.nextDouble() uses '.' not ','
                "-Duser.country=US",                 // (prevents InputMismatchException on European-locale servers)
                "-cp", classpath,
                "Main"                               // Entry point defined by exam
        );
    }

    /** Verifies exam .class files against stored checksums by inspecting the student's JAR.
     *
     * <p>Each entry in {@code originalChecksums} (filename → MD5) represents a file that the
     * exam provided.  If the student's JAR contains a file with the same name but a different
     * MD5, we treat it as a tamper attempt (they recompiled or altered the file).</p>
     *
     * @param studentJar        path to the student's .jar file
     * @param originalChecksums map of filename → MD5 computed from exam .class files
     * @return a tamper {@link ExecutionResult} if mismatch found, or {@code null} if clean
     */
    private ExecutionResult verifyExamFiles(Path studentJar,
                                            Map<String, String> originalChecksums) {
        List<String> tampered = new ArrayList<>();
        try {
            // Extract relevant .class files from student JAR (only those in originalChecksums)
            Map<String, String> studentChecksums = extractJarClassChecksums(studentJar,
                    originalChecksums.keySet());

            for (Map.Entry<String, String> entry : originalChecksums.entrySet()) {
                String filename = entry.getKey();
                String examChecksum = entry.getValue();

                if (!studentChecksums.containsKey(filename)) {
                    // File not present in student JAR — student removed an exam class file
                    tampered.add(filename + " (missing from student submission)");
                } else if (!studentChecksums.get(filename).equals(examChecksum)) {
                    // MD5 mismatch — student modified and recompiled the exam class
                    tampered.add(filename + " (modified)");
                }
            }
        } catch (IOException e) {
            log.warn("Could not inspect student JAR for tamper check: {}", e.getMessage());
            // Fail-safe: do not block grading if JAR inspection itself fails
            return null;
        }

        if (!tampered.isEmpty()) {
            return ExecutionResult.tamperedExamFiles(String.join(", ", tampered));
        }
        return null; // No tampering detected
    }

    /**
     * Extracts MD5 checksums for specific .class files found inside a JAR archive.
     *
     * @param jarPath   path to the .jar file
     * @param filenames set of filenames (e.g. "Main.class") to look for inside the JAR
     * @return map of filename → MD5 hex string for each matching entry found in the JAR
     */
    private Map<String, String> extractJarClassChecksums(Path jarPath,
                                                          java.util.Set<String> filenames)
            throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        try (var jis = new java.util.jar.JarInputStream(
                new java.io.BufferedInputStream(Files.newInputStream(jarPath)))) {
            java.util.jar.JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                // Match by simple filename only (strip any package path)
                String entryName = entry.getName();
                String simpleName = entryName.contains("/")
                        ? entryName.substring(entryName.lastIndexOf('/') + 1)
                        : entryName;
                if (filenames.contains(simpleName) && simpleName.endsWith(".class")) {
                    byte[] bytes = jis.readAllBytes();
                    result.put(simpleName, md5Hex(bytes));
                }
            }
        }
        return result;
    }

    /** Reads a stream in a background thread to prevent buffer deadlock. */
    private Thread captureStream(InputStream stream, StringWriter writer) {
        return new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream), 8192)) {
                int totalBytes = 0;
                String line;
                while ((line = reader.readLine()) != null) {
                    totalBytes += line.length();
                    if (totalBytes < MAX_OUTPUT_BYTES) {
                        writer.write(line);
                        writer.write("\n");
                    }
                }
            } catch (IOException ignored) {}
        });
    }

    /** Truncates a string to maxBytes to prevent oversized outputs. */
    private String truncate(String s, int maxBytes) {
        if (s == null) return "";
        if (s.length() <= maxBytes) return s;
        return s.substring(0, maxBytes) + "\n[... output truncated]";
    }

    /** Computes MD5 checksum and returns it as a lowercase hex string. */
    private String md5Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
