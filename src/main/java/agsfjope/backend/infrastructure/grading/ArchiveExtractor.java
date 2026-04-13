package agsfjope.backend.infrastructure.grading;

import agsfjope.backend.infrastructure.storage.MinioService;
import agsfjope.backend.infrastructure.storage.parser.RarExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Utility that downloads archives from MinIO and extracts specific entries
 * into a temporary working directory for the grading process.
 *
 * <p>Supports both .zip and .rar submission archives.</p>
 * <p>Temp directories are created under {@code System.getProperty("java.io.tmpdir")}/grading/</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArchiveExtractor {

    private final MinioService minioService;

    private static final String TEMP_BASE = System.getProperty("java.io.tmpdir") + "/grading/";

    // ─── PUBLIC API ──────────────────────────────────────────────────────────

    /**
     * Downloads the submission archive from MinIO and extracts the student's .jar
     * for a specific question number into a temporary directory.
     *
     * <p>Expected archive path inside zip: {@code {n}/run/Q{n}.jar}</p>
     *
     * @param bucketName      MinIO bucket (e.g., "submissions")
     * @param objectPath      path to the archive in MinIO (e.g., "submissions/uuid/sub.zip")
     * @param questionNumber  question number to extract JAR for
     * @param extension       ".zip" or ".rar"
     * @param workDir         temp directory to extract into
     * @return path to the extracted .jar file, or null if not found in archive
     */
    public Path extractStudentJar(String bucketName, String objectPath,
                                   int questionNumber, String extension, Path workDir) throws IOException {
        Path archivePath = downloadToTemp(bucketName, objectPath, workDir, "submission" + extension);
        String destName  = "student_q" + questionNumber + ".jar";
        int    q         = questionNumber;

        // Tier 1 — exact root with /run/: {n}/run/*.jar | Q{n}/run/*.jar | q{n}/run/*.jar
        Path result = extractFirstMatchingEntry(archivePath, extension, workDir, destName,
                entry -> (entry.startsWith(q + "/run/")
                       || entry.startsWith("Q" + q + "/run/")
                       || entry.startsWith("q" + q + "/run/"))
                       && entry.toLowerCase().endsWith(".jar"));
        if (result != null) return result;

        // Tier 1b — outer-folder-wrapped WITH /run/: AnythingFolder/{n}/run/*.jar
        // Handles: StudentName/1/run/Q1.jar or BaiNop/Q1/run/Q1.jar
        result = extractFirstMatchingEntry(archivePath, extension, workDir, destName,
                entry -> (entry.contains("/" + q + "/run/")
                       || entry.toLowerCase().contains("/q" + q + "/run/"))
                       && entry.toLowerCase().endsWith(".jar"));
        if (result != null) {
            log.warn("[EXTRACT] Q{}: JAR found via outer-folder-wrapped path (with /run/): {}",
                     q, result.getFileName());
            return result;
        }

        // Tier 2 — exact root WITHOUT /run/: any *.jar directly under {n}/ | Q{n}/
        result = extractFirstMatchingEntry(archivePath, extension, workDir, destName,
                entry -> (entry.startsWith(q + "/")
                       || entry.startsWith("Q" + q + "/")
                       || entry.startsWith("q" + q + "/"))
                       && entry.toLowerCase().endsWith(".jar"));
        if (result != null) {
            log.warn("[EXTRACT] Q{}: JAR found at question root (no /run/ subfolder).", q);
            return result;
        }

        // Tier 2b — outer-folder-wrapped WITHOUT /run/: AnythingFolder/{n}/*.jar
        result = extractFirstMatchingEntry(archivePath, extension, workDir, destName,
                entry -> (entry.contains("/" + q + "/")
                       || entry.toLowerCase().contains("/q" + q + "/"))
                       && entry.toLowerCase().endsWith(".jar"));
        if (result != null) {
            log.warn("[EXTRACT] Q{}: JAR found via outer-folder-wrapped path (no /run/).", q);
            return result;
        }

        // Tier 3 — last resort: root-level *.jar OR filename is Q{n}.jar / {n}.jar
        result = extractFirstMatchingEntry(archivePath, extension, workDir, destName,
                entry -> entry.toLowerCase().endsWith(".jar")
                       && (!entry.contains("/")
                          || entry.toLowerCase().endsWith("/q" + q + ".jar")
                          || entry.toLowerCase().endsWith("/" + q + ".jar")));
        if (result != null) {
            log.warn("[EXTRACT] Q{}: JAR found via last-resort filename match.", q);
            return result;
        }

        // All tiers exhausted — log archive structure to help diagnose
        logAllJarEntries(archivePath, extension, q);
        return null;
    }

    /**
     * Logs every .jar file found in the archive — called when extraction fails so
     * an admin can see the actual structure of the student's submission.
     */
    private void logAllJarEntries(Path archivePath, String extension, int questionNumber) {
        log.warn("[EXTRACT] Q{}: No JAR found. Listing all .jar entries in archive:", questionNumber);
        try {
            if (".zip".equalsIgnoreCase(extension)) {
                try (ZipFile zip = new ZipFile(archivePath.toFile())) {
                    zip.stream()
                       .filter(e -> !e.isDirectory() && e.getName().toLowerCase().endsWith(".jar"))
                       .forEach(e -> log.warn("[EXTRACT]   jar entry: {}", e.getName()));
                    if (zip.stream().noneMatch(e -> e.getName().toLowerCase().endsWith(".jar"))) {
                        log.warn("[EXTRACT]   (no .jar files found at all — listing first 20 entries)");
                        zip.stream().limit(20).forEach(e -> log.warn("[EXTRACT]   entry: {}", e.getName()));
                    }
                }
            }
        } catch (IOException ex) {
            log.warn("[EXTRACT] Q{}: Could not list archive entries: {}", questionNumber, ex.getMessage());
        }
    }


    /**
     * Downloads the exam paper archive from MinIO and extracts all .class files
     * for a specific question into a temporary directory.
     *
     * <p>Expected archive path inside zip: {@code q{n}/*.class}</p>
     *
     * @param bucketName     MinIO bucket (e.g., "exam-papers")
     * @param objectPath     path to the exam paper archive in MinIO
     * @param questionNumber question number to extract .class files for
     * @param extension      ".zip" or ".rar"
     * @param workDir        temp directory to extract into (exam classes go into workDir/exam_q{n}/)
     * @return path to the directory containing exam .class files
     */
    public Path extractExamClasses(String bucketName, String objectPath,
                                   int questionNumber, String extension, Path workDir) throws IOException {
        Path archivePath = downloadToTemp(bucketName, objectPath, workDir, "exampaper" + extension);
        Path examDir = workDir.resolve("exam_q" + questionNumber);
        Files.createDirectories(examDir);

        String classPrefix1 = "q" + questionNumber + "/";
        String classPrefix2 = "Q" + questionNumber + "/";

        extractAllMatchingEntries(archivePath, extension, examDir,
                entry -> (entry.startsWith(classPrefix1) || entry.startsWith(classPrefix2))
                        && entry.toLowerCase().endsWith(".class"));

        return examDir;
    }

    /**
     * Downloads the submission archive and extracts all .java source files
     * for a specific question (for AI review).
     *
     * <p>Expected archive path inside zip: {@code {n}/src/*.java}</p>
     *
     * @param bucketName     MinIO bucket
     * @param objectPath     path to the submission archive in MinIO
     * @param questionNumber question number
     * @param extension      ".zip" or ".rar"
     * @param workDir        temp directory
     * @return path to directory containing student's .java files, or null if none found
     */
    public Path extractStudentSources(String bucketName, String objectPath,
                                      int questionNumber, String extension, Path workDir) throws IOException {
        Path archivePath = downloadToTemp(bucketName, objectPath, workDir, "submission" + extension);
        Path srcDir = workDir.resolve("src_q" + questionNumber);
        Files.createDirectories(srcDir);

        int q = questionNumber;

        // [DEBUG] Dump ZIP entry paths to see actual structure for diagnosis
        logArchiveEntries(archivePath, extension, q);

        // Single-pass: collect every .java that belongs to this question,
        // regardless of how deep or how the student structured their zip.
        //
        // Covers (all applied simultaneously):
        //   Tier 1 — exact root   : {n}/src/*.java  |  Q{n}/src/*.java
        //   Tier 2 — outer-wrapped: .../n/src/*.java |  .../Q{n}/src/*.java
        //   Tier 3 — last-resort  : any .java anywhere under {n}/ or Q{n}/
        //                           handles src/src/, double-zip, full NetBeans project dump, etc.
        int extracted = extractAllMatchingEntries(archivePath, extension, srcDir,
                entry -> entry.toLowerCase().endsWith(".java")
                        && (entry.startsWith(q + "/")
                           || entry.startsWith("Q" + q + "/")
                           || entry.contains("/" + q + "/")
                           || entry.toLowerCase().contains("/q" + q + "/")));

        if (extracted > 0) {
            log.warn("[EXTRACT-SRC] Q{}: extracted {} .java file(s).", q, extracted);
            return srcDir;
        }

        log.warn("[EXTRACT-SRC] Q{}: no .java files found in archive.", q);
        return null;
    }

    /** Logs the first 30 entry names in the archive for diagnosis. */
    private void logArchiveEntries(Path archivePath, String extension, int q) {
        try {
            if (".zip".equalsIgnoreCase(extension)) {
                try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(archivePath.toFile())) {
                    log.warn("[ARCHIVE-ENTRIES] Q{} — listing entries in {}:", q, archivePath.getFileName());
                    zf.stream()
                      .filter(e -> !e.isDirectory())
                      .limit(30)
                      .forEach(e -> log.warn("  entry: '{}'", e.getName()));
                }
            }
        } catch (Exception ex) {
            log.warn("[ARCHIVE-ENTRIES] Could not list archive: {}", ex.getMessage());
        }
    }



    /**
     * Creates a fresh temporary working directory for a single grading job.
     * Format: /tmp/grading/{submissionId}_{questionNumber}/
     *
     * @param key unique key for this grading job (e.g., submissionId_q1)
     * @return path to the temp directory
     */
    public Path createWorkDir(String key) throws IOException {
        Path workDir = Paths.get(TEMP_BASE + key);
        Files.createDirectories(workDir);
        return workDir;
    }

    /**
     * Deletes the temporary working directory and all its contents.
     * Called after grading is complete to free up disk space.
     *
     * @param workDir the temp directory to delete
     */
    public void cleanupWorkDir(Path workDir) {
        if (workDir == null || !Files.exists(workDir)) return;
        try {
            try (var walk = Files.walk(workDir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
            log.debug("Cleaned up temp dir: {}", workDir);
        } catch (IOException e) {
            log.warn("Failed to clean up temp dir {}: {}", workDir, e.getMessage());
        }
    }

    // ─── PRIVATE HELPERS ─────────────────────────────────────────────────────

    /** Downloads a MinIO object to a local temp file in workDir. */
    private Path downloadToTemp(String bucket, String objectPath,
                                 Path workDir, String localFileName) throws IOException {
        Path dest = workDir.resolve(localFileName);
        // Re-use if already downloaded for this workDir
        if (Files.exists(dest)) return dest;

        try (InputStream in = minioService.downloadFile(bucket, objectPath)) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        log.debug("Downloaded {} → {}", objectPath, dest);
        return dest;
    }

    /** Extracts the first archive entry matching the predicate to destDir/destName. */
    private Path extractFirstMatchingEntry(Path archivePath, String extension,
                                           Path destDir, String destName,
                                           java.util.function.Predicate<String> matcher) throws IOException {
        Path dest = destDir.resolve(destName);
        if (".zip".equalsIgnoreCase(extension)) {
            return extractFirstFromZip(archivePath, dest, matcher);
        } else {
            return extractFirstFromRar(archivePath, dest, matcher);
        }
    }

    /** Extracts ALL matching entries from archive into destDir, returns count. */
    private int extractAllMatchingEntries(Path archivePath, String extension,
                                          Path destDir,
                                          java.util.function.Predicate<String> matcher) throws IOException {
        if (".zip".equalsIgnoreCase(extension)) {
            return extractAllFromZip(archivePath, destDir, matcher);
        } else {
            return extractAllFromRar(archivePath, destDir, matcher);
        }
    }

    private Path extractFirstFromZip(Path archivePath, Path dest,
                                     java.util.function.Predicate<String> matcher) throws IOException {
        try (ZipFile zip = new ZipFile(archivePath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && matcher.test(normalizeSlash(entry.getName()))) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return dest;
                }
            }
        }
        return null; // Not found
    }

    private int extractAllFromZip(Path archivePath, Path destDir,
                                  java.util.function.Predicate<String> matcher) throws IOException {
        int count = 0;
        try (ZipFile zip = new ZipFile(archivePath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && matcher.test(normalizeSlash(entry.getName()))) {
                    String fileName = Paths.get(entry.getName()).getFileName().toString();
                    Path dest = destDir.resolve(fileName);
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                    count++;
                }
            }
        }
        return count;
    }

    private Path extractFirstFromRar(Path archivePath, Path dest,
                                     java.util.function.Predicate<String> matcher) throws IOException {
        byte[] data = RarExtractor.extractFirstMatching(archivePath, matcher);
        if (data == null) return null;
        java.nio.file.Files.write(dest, data);
        return dest;
    }

    private int extractAllFromRar(Path archivePath, Path destDir,
                                  java.util.function.Predicate<String> matcher) throws IOException {
        int[] count = {0};
        RarExtractor.iterateEntries(archivePath, (name, bytes) -> {
            if (bytes.length == 0 && name.endsWith("/")) return; // directory
            if (!matcher.test(name)) return;
            String fileName = java.nio.file.Paths.get(name).getFileName().toString();
            Path dest = destDir.resolve(fileName);
            try {
                java.nio.file.Files.write(dest, bytes);
                count[0]++;
            } catch (IOException e) {
                throw new RuntimeException("Failed to write " + fileName + ": " + e.getMessage(), e);
            }
        });
        return count[0];
    }

    private String normalizeSlash(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }
}
