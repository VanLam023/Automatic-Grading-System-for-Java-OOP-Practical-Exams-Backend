package agsfjope.backend.infrastructure.storage.parser;

import agsfjope.backend.core.exceptions.exampaper.InvalidZipStructureException;
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
 * <p>Expected archive structure (both with and without outer wrapper folder):
 * <pre>
 *   [WrapperFolder/]            &lt;-- optional outer wrapper folder (e.g., PRO_given_1/)
 *     {n}/
 *       run/
 *         Q{n}.jar              &lt;-- compiled .jar (exactly one)
 *       src/
 *         *.java                &lt;-- student source files (zero or more)
 * </pre>
 *
 * <p>Parsing rules:
 * <ul>
 *   <li>Outer wrapper folders (non-numeric top-level) are automatically stripped.</li>
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
        Map<Integer, String> jarPaths       = new TreeMap<>();
        Map<Integer, List<String>> srcPaths = new TreeMap<>();

        // First pass: detect the outer wrapper folder prefix (if any)
        String wrapperPrefix = detectWrapperPrefix(tmpFile);
        log.debug("SubmissionParser: wrapperPrefix='{}'", wrapperPrefix);

        try (ZipFile zip = new ZipFile(tmpFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = normalizeSlash(entry.getName());
                if (entry.isDirectory()) continue;

                // Strip outer wrapper folder to get effective path
                String effective = stripPrefix(name, wrapperPrefix);

                Integer qNum = extractQuestionNumber(effective);
                if (qNum == null) continue;

                if (isJarEntry(effective, qNum)) {
                    // Store effective path (logical path inside zip, stripped of wrapper)
                    jarPaths.putIfAbsent(qNum, effective);
                } else if (isJavaSourceEntry(effective, qNum)) {
                    srcPaths.computeIfAbsent(qNum, k -> new ArrayList<>()).add(effective);
                }
                // Other files (nbproject, build, .class) are silently ignored
            }
        }

        return buildResult(jarPaths, srcPaths);
    }

    // ─── RAR ───────────────────────────────────────────────────────────────────────────────

    /**
     * Parses a RAR archive (RAR4 or RAR5) using sevenzipjbinding (7-Zip JNI wrapper).
     * Single-pass: reads all entry names first (from map keys), then classifies.
     */
    private ParsedSubmission parseRar(Path tmpFile) throws IOException {
        Map<Integer, String> jarPaths       = new TreeMap<>();
        Map<Integer, List<String>> srcPaths = new TreeMap<>();

        // Collect all entry names first (for wrapper detection)
        List<String> allPaths = new ArrayList<>();
        RarExtractor.iterateEntries(tmpFile, (name, bytes) -> {
            if (bytes.length > 0 || !name.endsWith("/")) {
                allPaths.add(name);
            }
        });

        String wrapperPrefix = detectWrapperPrefixFromPaths(allPaths);
        log.debug("SubmissionParser (RAR): wrapperPrefix='{}'", wrapperPrefix);

        // Classify entries
        RarExtractor.iterateEntries(tmpFile, (name, bytes) -> {
            if (bytes.length == 0 && name.endsWith("/")) return; // directory
            String effective = stripPrefix(name, wrapperPrefix);

            Integer qNum = extractQuestionNumber(effective);
            if (qNum == null) return;

            if (isJarEntry(effective, qNum)) {
                jarPaths.putIfAbsent(qNum, effective);
            } else if (isJavaSourceEntry(effective, qNum)) {
                srcPaths.computeIfAbsent(qNum, k -> new ArrayList<>()).add(effective);
            }
        });

        return buildResult(jarPaths, srcPaths);
    }

    // ─── Wrapper Detection ────────────────────────────────────────────────────

    /**
     * Detects an outer (non-numeric) wrapper folder that wraps all question folders.
     * E.g., if all entries start with "PRO_given_1/", returns "PRO_given_1/".
     * Returns "" (empty string) if there's no wrapper.
     */
    private String detectWrapperPrefix(Path tmpFile) throws IOException {
        List<String> paths = new ArrayList<>();
        try (ZipFile zip = new ZipFile(tmpFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (!e.isDirectory()) {
                    paths.add(normalizeSlash(e.getName()));
                }
            }
        }
        return detectWrapperPrefixFromPaths(paths);
    }

    private String detectWrapperPrefixFromPaths(List<String> paths) {
        if (paths.isEmpty()) return "";

        // Get all unique top-level folders
        java.util.Set<String> topFolders = new java.util.HashSet<>();
        for (String p : paths) {
            int slash = p.indexOf('/');
            if (slash > 0) {
                topFolders.add(p.substring(0, slash));
            }
        }

        // If there is exactly ONE non-numeric top-level folder, treat it as wrapper
        if (topFolders.size() == 1) {
            String folder = topFolders.iterator().next();
            try {
                Integer.parseInt(folder);
                return ""; // It IS a number (e.g., "1") — no wrapper
            } catch (NumberFormatException e) {
                log.debug("SubmissionParser: detected outer wrapper folder '{}'", folder);
                return folder + "/";
            }
        }

        // Multiple top-level folders — check if any are non-numeric (could be partial wrapper)
        // Look for a common prefix shared by all entries
        if (!paths.isEmpty()) {
            String firstPath = paths.get(0);
            int slash = firstPath.indexOf('/');
            if (slash > 0) {
                String candidateFolder = firstPath.substring(0, slash);
                String candidatePrefix = candidateFolder + "/";
                boolean allMatch = paths.stream().allMatch(p -> p.startsWith(candidatePrefix));
                if (allMatch) {
                    // Check this is non-numeric
                    try {
                        Integer.parseInt(candidateFolder);
                        return ""; // It IS a number — no wrapper
                    } catch (NumberFormatException e) {
                        log.debug("SubmissionParser: detected common wrapper prefix '{}'", candidatePrefix);
                        return candidatePrefix;
                    }
                }
            }
        }

        return "";
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String stripPrefix(String path, String prefix) {
        if (prefix.isEmpty() || !path.startsWith(prefix)) return path;
        return path.substring(prefix.length());
    }

    /**
     * Extracts the question number from an entry path like "3/run/Q3.jar" → 3.
     * Returns null if the entry is not inside a numeric top-level folder.
     */
    private Integer extractQuestionNumber(String path) {
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
        String prefix = qNum + "/run/";
        if (!path.startsWith(prefix)) return false;
        String rest = path.substring(prefix.length());
        return !rest.contains("/") && rest.toLowerCase().endsWith(".jar");
    }

    /**
     * Returns true if the entry is a .java file inside the {@code src/} folder of a question.
     */
    private boolean isJavaSourceEntry(String path, int qNum) {
        String prefix = qNum + "/src/";
        if (!path.startsWith(prefix)) return false;
        String rest = path.substring(prefix.length());
        return rest.toLowerCase().endsWith(".java") && !rest.isBlank();
    }

    private String normalizeSlash(String path) {
        return path.replace('\\', '/');
    }

    private ParsedSubmission buildResult(
            Map<Integer, String> jarPaths,
            Map<Integer, List<String>> srcPaths) {

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
