package agsfjope.backend.infrastructure.storage.parser;

import agsfjope.backend.core.exceptions.exampaper.InvalidZipStructureException;
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Parser for student submission archives (.zip or .rar).
 *
 * <p>Expected archive structure:
 * <pre>
 *   {n}/
 *     run/
 *       Q{n}.jar          ← compiled .jar (exactly one)
 *     src/
 *       *.java            ← student source files (zero or more)
 *       *.class           ← precompiled files from exam paper (ignored for AI review)
 * </pre>
 *
 * <p>Parsing rules:
 * <ul>
 *   <li>Extra files/folders outside the above structure are silently ignored.</li>
 *   <li>If {@code run/} has no .jar → {@code jarEntryPath = null} (0 test-case score).</li>
 *   <li>If {@code src/} has no .java → {@code sourceEntryPaths} is empty (AI skipped).</li>
 *   <li>Question folders that are missing entirely are NOT reported here; the service
 *       handles that by comparing parsed answers against the exam paper's question list.</li>
 * </ul>
 */
@Slf4j
@Component
public class SubmissionZipParser {

    /**
     * Parses a submission from a temp file path.
     *
     * @param tmpFile   path to local temp copy of the archive
     * @param extension ".zip" or ".rar" (lowercase)
     * @return parsed submission with one ParsedAnswer per found question folder
     * @throws InvalidZipStructureException if the archive cannot be read
     */
    public ParsedSubmission parseFromTempFile(Path tmpFile, String extension) {
        try {
            if (".zip".equals(extension)) {
                return parseZip(tmpFile);
            } else {
                return parseRar(tmpFile);
            }
        } catch (InvalidZipStructureException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidZipStructureException(
                    "Không thể đọc file bài nộp: " + e.getMessage(), e);
        }
    }

    // ─── ZIP ─────────────────────────────────────────────────────────────────

    private ParsedSubmission parseZip(Path tmpFile) throws IOException {
        // questionNumber → (jarPath, list of sourcePaths)
        Map<Integer, String> jarPaths    = new TreeMap<>();
        Map<Integer, List<String>> srcPaths = new TreeMap<>();

        try (ZipFile zip = new ZipFile(tmpFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = normalizeSlash(entry.getName());
                if (entry.isDirectory()) continue;

                // Match pattern: {n}/run/*.jar
                Integer qNum = extractQuestionNumber(name);
                if (qNum == null) continue;

                if (isJarEntry(name, qNum)) {
                    // Only keep the first jar found per question
                    jarPaths.putIfAbsent(qNum, name);
                } else if (isJavaSourceEntry(name, qNum)) {
                    srcPaths.computeIfAbsent(qNum, k -> new ArrayList<>()).add(name);
                }
                // Other files (nbproject, build, .class) are silently ignored
            }
        }

        return buildResult(jarPaths, srcPaths);
    }

    // ─── RAR ─────────────────────────────────────────────────────────────────

    private ParsedSubmission parseRar(Path tmpFile) throws Exception {
        Map<Integer, String> jarPaths    = new TreeMap<>();
        Map<Integer, List<String>> srcPaths = new TreeMap<>();

        try (Archive archive = new Archive(tmpFile.toFile())) {
            for (FileHeader fh : archive.getFileHeaders()) {
                if (fh.isDirectory()) continue;
                String name = normalizeSlash(fh.getFileName());

                Integer qNum = extractQuestionNumber(name);
                if (qNum == null) continue;

                if (isJarEntry(name, qNum)) {
                    jarPaths.putIfAbsent(qNum, name);
                } else if (isJavaSourceEntry(name, qNum)) {
                    srcPaths.computeIfAbsent(qNum, k -> new ArrayList<>()).add(name);
                }
            }
        }

        return buildResult(jarPaths, srcPaths);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Extracts the question number from an entry path like "3/run/Q3.jar" → 3.
     * Returns null if the entry is not inside a numeric top-level folder.
     */
    private Integer extractQuestionNumber(String path) {
        // Expect: "{digits}/..." at start of path
        int slash = path.indexOf('/');
        if (slash <= 0) return null;
        String folder = path.substring(0, slash);
        try {
            int n = Integer.parseInt(folder);
            return n > 0 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Returns true if the entry is a .jar inside the {@code run/} folder of a question.
     * E.g., "3/run/Q3.jar" → true; ignores jar files in any other folder.
     */
    private boolean isJarEntry(String path, int qNum) {
        // Must be: {qNum}/run/*.jar (only one level deep under run/)
        String prefix = qNum + "/run/";
        if (!path.startsWith(prefix)) return false;
        String rest = path.substring(prefix.length());
        return !rest.contains("/") && rest.toLowerCase().endsWith(".jar");
    }

    /**
     * Returns true if the entry is a .java file inside the {@code src/} folder of a question.
     * Nested sub-folders inside src/ are ignored (only direct children).
     */
    private boolean isJavaSourceEntry(String path, int qNum) {
        String prefix = qNum + "/src/";
        if (!path.startsWith(prefix)) return false;
        String rest = path.substring(prefix.length());
        // Accept direct .java children only (skip sub-folders like src/subpkg/Foo.java is ok too)
        return rest.toLowerCase().endsWith(".java") && !rest.isBlank();
    }

    private String normalizeSlash(String path) {
        return path.replace('\\', '/');
    }

    private ParsedSubmission buildResult(
            Map<Integer, String> jarPaths,
            Map<Integer, List<String>> srcPaths) {

        // Merge: collect all question numbers found in either map
        java.util.Set<Integer> allQuestions = new java.util.TreeSet<>();
        allQuestions.addAll(jarPaths.keySet());
        allQuestions.addAll(srcPaths.keySet());

        List<ParsedSubmission.ParsedAnswer> answers = new ArrayList<>();
        for (int qNum : allQuestions) {
            String jar = jarPaths.get(qNum);
            List<String> sources = srcPaths.getOrDefault(qNum, List.of());
            answers.add(new ParsedSubmission.ParsedAnswer(qNum, jar, sources));
            log.debug("SubmissionParser: Q{} → jar={}, sources={}", qNum, jar, sources.size());
        }

        log.info("SubmissionParser: Parsed {} question folders.", answers.size());
        return new ParsedSubmission(answers);
    }
}
