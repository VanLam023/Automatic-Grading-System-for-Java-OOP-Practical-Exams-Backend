package agsfjope.backend.infrastructure.storage.parser;

import agsfjope.backend.core.exceptions.exampaper.InvalidZipStructureException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses exam paper archives (.zip or .rar) into a {@link ParsedExamPaper} structure.
 *
 * <h2>Expected archive structure</h2>
 * <pre>
 *   archive.zip  (or .rar)
 *   └── &lt;AnyFolderName&gt;/         ← root folder (name is arbitrary)
 *       ├── 1/                    ← question folder (named with integer ≥ 1)
 *       │   ├── Q1.docx           ← question document (REQUIRED)
 *       │   ├── tc1.txt           ← test cases (REQUIRED)
 *       │   └── src/ ...          ← reference source/class files (optional, stored for grading)
 *       ├── 2/
 *       │   ├── Q2.docx
 *       │   └── tc2.txt
 *       └── ...
 * </pre>
 *
 * <h2>Validation rules enforced</h2>
 * <ul>
 *   <li>Archive must not be empty and must contain a root folder.</li>
 *   <li>At least one numbered question sub-folder must exist.</li>
 *   <li>Each question folder must contain a {@code Q{n}.docx} and a {@code tc{n}.txt}.</li>
 *   <li>The {@code Q{n}.docx} must contain a parseable score value (pattern: digits/decimal after key phrases).</li>
 *   <li>Each {@code tc{n}.txt} must contain at least one valid {@code INPUT:}/{@code OUTPUT:} pair.</li>
 *   <li>Each {@code OUTPUT:} block must not be empty.</li>
 * </ul>
 *
 * <h2>tc{n}.txt format</h2>
 * <pre>
 *   INPUT:
 *   line1
 *   line2
 *   OUTPUT:
 *   expected output line
 *   INPUT:
 *   ...
 *   REMOVE_SPACES:
 *   YES
 *   CASE_SENSITIVE:
 *   YES
 * </pre>
 */
@Slf4j
@Component
public class ZipExamPaperParser {


    /**
     * Regex to find a score/mark value inside a .docx paragraph.
     * Matches patterns like: "(2 marks)", "2 marks", "Score: 2", "2 điểm", "2.0 points"
     */
    private static final Pattern SCORE_PATTERN = Pattern.compile(
            "(?:mark[s]?|score|point[s]?|điểm)[:\\s]*([0-9]+(?:[.,][0-9]+)?)" +
            "|([0-9]+(?:[.,][0-9]+)?)\\s*(?:mark[s]?|score|point[s]?|điểm)",
            Pattern.CASE_INSENSITIVE);

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Parses the given archive file input stream (must be a .zip file bytes) into a
     * {@link ParsedExamPaper}. For .rar support the caller should first save to a temp file
     * then call {@link #parseFromTempFile(Path, String)}.
     *
     * <p>This method is used for .zip files where the entire content is available as a stream.</p>
     *
     * @param zipInputStream the raw bytes of a .zip archive
     * @return parsed exam paper structure
     * @throws InvalidZipStructureException if the archive does not meet the expected format
     * @throws IOException                  if an I/O error occurs while reading the archive
     */
    public ParsedExamPaper parseZip(InputStream zipInputStream) throws IOException {
        // Write to a temp file so ZipFile (commons-compress random-access) can work
        Path tmpFile = Files.createTempFile("exam-paper-", ".zip");
        try {
            Files.copy(zipInputStream, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            return parseFromTempFile(tmpFile, ".zip");
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    /**
     * Parses the given archive from a temp file. Supports both .zip and .rar.
     *
     * @param tmpFile   path to the temp file
     * @param extension lowercase extension (".zip" or ".rar")
     * @return parsed exam paper structure
     * @throws InvalidZipStructureException if structure is invalid
     * @throws IOException                  on I/O error
     */
    public ParsedExamPaper parseFromTempFile(Path tmpFile, String extension) throws IOException {
        if (".zip".equalsIgnoreCase(extension)) {
            return parseZipFile(tmpFile);
        } else if (".rar".equalsIgnoreCase(extension)) {
            return parseRarFile(tmpFile);
        } else {
            throw new InvalidZipStructureException(
                    "Định dạng file không được hỗ trợ: '" + extension + "'. Chỉ hỗ trợ .zip và .rar.");
        }
    }

    // ─── ZIP Parsing ──────────────────────────────────────────────────────────

    private ParsedExamPaper parseZipFile(Path zipPath) throws IOException {
        // Collect all entries from the zip
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zipFile = ZipFile.builder().setFile(zipPath.toFile()).get()) {
            Enumeration<ZipArchiveEntry> en = zipFile.getEntries();
            while (en.hasMoreElements()) {
                ZipArchiveEntry entry = en.nextElement();
                if (!entry.isDirectory()) {
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        entries.put(normalizeSlash(entry.getName()), is.readAllBytes());
                    }
                } else {
                    // Track directories too (as empty byte array) so we can detect folders
                    entries.put(normalizeSlash(entry.getName()), new byte[0]);
                }
            }
        }

        if (entries.isEmpty()) {
            throw new InvalidZipStructureException("File nén rỗng — không tìm thấy nội dung bên trong.");
        }

        return parseEntries(entries);
    }

    // ─── RAR Parsing ─────────────────────────────────────────────────────────

    /**
     * Parses a RAR archive (RAR4 or RAR5) using sevenzipjbinding (7-Zip JNI wrapper).
     * Supports both RAR generations without any external tool installation.
     */
    private ParsedExamPaper parseRarFile(Path rarPath) throws IOException {
        Map<String, byte[]> entries = RarExtractor.readAllEntries(rarPath);

        if (entries.isEmpty()) {
            throw new InvalidZipStructureException("File nén rỗng — không tìm thấy nội dung bên trong.");
        }

        return parseEntries(entries);
    }

    // ─── Core Parsing Logic ───────────────────────────────────────────────────

    /**
     * Finds the root folder and question sub-folders, then delegates to question parsers.
     *
     * <p>Supports both flat and nested structures, e.g.:</p>
     * <pre>
     *   Flat:    MyExam/1/Q1.docx
     *   Nested:  MyExam/PaperNo_3/1/Q1.docx
     * </pre>
     */
    private ParsedExamPaper parseEntries(Map<String, byte[]> entries) throws IOException {
        // Find the actual folder prefix that directly contains numbered question folders.
        // This handles both flat (root/1/) and nested (root/PaperNo_3/1/) structures.
        String questionRoot = findQuestionRoot(entries);
        log.debug("ZipParser: detected question root = '{}'", questionRoot);

        // Discover numbered question folders (1/, 2/, 3/, ...) under the question root
        Set<Integer> questionNumbers = findQuestionNumbers(entries, questionRoot);
        if (questionNumbers.isEmpty()) {
            throw new InvalidZipStructureException(
                    "Không tìm thấy thư mục câu hỏi (1/, 2/, ...) bên trong '" + questionRoot +
                    "'. Hãy kiểm tra lại cấu trúc file nén.\n" +
                    "Cấu trúc hợp lệ: <TênFile>/<TênThùMục>/1/, 2/, ... hoặc <TênFile>/1/, 2/, ...");
        }

        // Parse each question
        List<ParsedExamPaper.ParsedQuestion> questions = new ArrayList<>();
        for (int qNum : questionNumbers.stream().sorted().toList()) {
            ParsedExamPaper.ParsedQuestion q = parseQuestion(entries, questionRoot, qNum);
            questions.add(q);
        }

        log.info("ZipParser: parsed {} questions, {} total test cases",
                questions.size(), questions.stream().mapToInt(q -> q.testCases().size()).sum());
        return new ParsedExamPaper(questions);
    }

    /**
     * Finds the deepest folder prefix that directly contains numbered question sub-folders (1/, 2/, ...).
     *
     * <p>Algorithm: collect all unique directory prefixes from all entry paths,
     * then for each prefix check if it has at least one numbered immediate child folder.
     * Returns the first match (BFS order by depth).</p>
     *
     * <p>This handles extra intermediate folders like {@code PaperNo_3/} between the archive
     * root and the actual question folders.</p>
     */
    private String findQuestionRoot(Map<String, byte[]> entries) {
        // Collect all unique folder prefixes present in the archive
        Set<String> allPrefixes = new LinkedHashSet<>();
        allPrefixes.add(""); // archive root (no prefix)
        for (String path : entries.keySet()) {
            // Add every ancestor directory of each entry
            String[] parts = path.split("/", -1);
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (!parts[i].isEmpty()) {
                    prefix.append(parts[i]).append("/");
                    allPrefixes.add(prefix.toString());
                }
            }
        }

        // Sort by depth (shallow first) so we return the shallowest valid parent
        List<String> sorted = allPrefixes.stream()
                .sorted(Comparator.comparingLong(p -> p.chars().filter(c -> c == '/').count()))
                .toList();

        for (String prefix : sorted) {
            Set<Integer> nums = findQuestionNumbers(entries, prefix);
            if (!nums.isEmpty()) {
                return prefix;
            }
        }

        // Fallback: return the top-level folder (findRootPrefix behavior)
        return findRootPrefix(entries);
    }

    /**
     * Determines the root folder prefix.
     * The root is the first path component that all entries share.
     * Example: "PaperNo_3/1/Q1.docx" → root = "PaperNo_3/"
     *
     * <p>If items are at the archive root (no common prefix), rootPrefix = "".</p>
     */
    private String findRootPrefix(Map<String, byte[]> entries) {
        // Collect all paths
        Set<String> allPaths = entries.keySet();

        // Try to find a common top-level directory
        Set<String> topLevelDirs = new LinkedHashSet<>();
        for (String path : allPaths) {
            int slash = path.indexOf('/');
            if (slash > 0) {
                topLevelDirs.add(path.substring(0, slash + 1));
            }
        }

        if (topLevelDirs.size() == 1) {
            return topLevelDirs.iterator().next();
        }

        // Multiple top-level entries or no subdirs → root is "" (files at archive root)
        return "";
    }

    /**
     * Finds all numbered question folders (e.g., "1/", "2/") under the root prefix.
     * A folder is a question folder if its name under root is a positive integer.
     */
    private Set<Integer> findQuestionNumbers(Map<String, byte[]> entries, String rootPrefix) {
        Set<Integer> numbers = new TreeSet<>();
        for (String path : entries.keySet()) {
            if (!path.startsWith(rootPrefix)) continue;
            String relative = path.substring(rootPrefix.length()); // e.g., "1/Q1.docx"
            if (relative.isEmpty()) continue;

            String[] parts = relative.split("/", -1);
            if (parts.length >= 1) {
                try {
                    int num = Integer.parseInt(parts[0].trim());
                    if (num > 0) numbers.add(num);
                } catch (NumberFormatException ignored) {
                    // Not a question folder
                }
            }
        }
        return numbers;
    }

    // ─── Question Parser ──────────────────────────────────────────────────────

    /**
     * Parses a single question folder: reads the .docx and tc{n}.txt file.
     */
    private ParsedExamPaper.ParsedQuestion parseQuestion(
            Map<String, byte[]> entries, String rootPrefix, int qNum) throws IOException {

        String questionPrefix = rootPrefix + qNum + "/";

        // ── Find and parse Q{n}.docx ─────────────────────────────────────────
        String docxKey = findEntryKey(entries, questionPrefix, "Q" + qNum + ".docx");
        if (docxKey == null) {
            throw new InvalidZipStructureException(
                    "Câu " + qNum + ": Thiếu file đề bài (Q" + qNum + ".docx) trong thư mục '" + questionPrefix + "'.");
        }

        DocxContent docxContent = parseDocx(entries.get(docxKey), qNum);

        // ── Find and parse tc{n}.txt ─────────────────────────────────────────
        String tcKey = findEntryKey(entries, questionPrefix, "tc" + qNum + ".txt");
        if (tcKey == null) {
            throw new InvalidZipStructureException(
                    "Câu " + qNum + ": Thiếu file test case (tc" + qNum + ".txt) trong thư mục '" + questionPrefix + "'.");
        }

        TcFileContent tcContent = parseTcFile(entries.get(tcKey), qNum, docxContent.maxScore());

        return new ParsedExamPaper.ParsedQuestion(
                qNum,
                docxContent.title(),
                docxContent.description(),
                docxContent.maxScore(),
                tcContent.removeSpaces(),
                tcContent.caseSensitive(),
                tcContent.testCases()
        );
    }

    // ─── DOCX Parser ─────────────────────────────────────────────────────────

    private record DocxContent(String title, String description, BigDecimal maxScore) {}

    /**
     * Parses a Q{n}.docx file to extract:
     * <ul>
     *   <li>Title: first non-empty text block (paragraph or table text)</li>
     *   <li>MaxScore: first number adjacent to keywords "marks", "score", "points", "điểm"</li>
     *   <li>Description: all remaining text blocks (paragraph + table cells) joined</li>
     * </ul>
     */
    private DocxContent parseDocx(byte[] docxBytes, int qNum) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            // Read both normal paragraphs and table contents in document order.
            // Why: many exam papers place core requirements in Word tables.
            List<String> textBlocks = extractDocxTextBlocks(doc);

            if (textBlocks.isEmpty()) {
                throw new InvalidZipStructureException(
                        "Câu " + qNum + ": File Q" + qNum + ".docx không có nội dung.");
            }

            String title = null;
            BigDecimal maxScore = null;
            StringBuilder descBuilder = new StringBuilder();
            boolean titleSet = false;

            for (String text : textBlocks) {
                if (text.isEmpty()) continue;

                if (!titleSet) {
                    title = text;
                    titleSet = true;
                } else {
                    descBuilder.append(text).append("\n");
                }

                // Search every paragraph for the score
                if (maxScore == null) {
                    maxScore = extractScore(text);
                }
            }

            if (title == null) {
                title = "Question " + qNum;
            }
            if (maxScore == null) {
                throw new InvalidZipStructureException(
                        "Câu " + qNum + ": Không tìm thấy điểm số (maxScore) trong file Q" + qNum +
                        ".docx. Hãy đảm bảo file có ghi điểm rõ ràng, ví dụ: '(2 marks)' hoặc 'Score: 2'.");
            }
            if (maxScore.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidZipStructureException(
                        "Câu " + qNum + ": Điểm số trong Q" + qNum + ".docx phải lớn hơn 0, nhưng tìm thấy: " + maxScore);
            }

            String description = descBuilder.toString().trim();
            return new DocxContent(title, description.isEmpty() ? null : description, maxScore);
        } catch (InvalidZipStructureException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidZipStructureException(
                    "Câu " + qNum + ": Không thể đọc file Q" + qNum + ".docx. File có thể bị hỏng: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts visible text blocks from a DOCX in body order.
     * Includes:
     * <ul>
     *   <li>Paragraph text</li>
     *   <li>Table text (flattened row-by-row, cell-by-cell)</li>
     * </ul>
     */
    private List<String> extractDocxTextBlocks(XWPFDocument doc) {
        List<String> blocks = new ArrayList<>();

        for (IBodyElement element : doc.getBodyElements()) {
            if (element.getElementType() == BodyElementType.PARAGRAPH) {
                XWPFParagraph para = (XWPFParagraph) element;
                String text = extractParagraphText(para);
                if (!text.isEmpty()) {
                    blocks.add(text);
                }
            } else if (element.getElementType() == BodyElementType.TABLE) {
                String tableText = flattenTableText((XWPFTable) element);
                if (!tableText.isBlank()) {
                    blocks.add(tableText);
                }
            }
        }

        return blocks;
    }

    /**
     * Flattens a Word table into plain text.
     *
     * <p>Format used:</p>
     * <ul>
     *   <li>Each row becomes one line.</li>
     *   <li>Cells in the same row are joined by {@code " | "} to preserve column boundaries.</li>
     * </ul>
     */
    private String flattenTableText(XWPFTable table) {
        StringBuilder sb = new StringBuilder();

        for (XWPFTableRow row : table.getRows()) {
            List<String> cellTexts = new ArrayList<>();

            for (XWPFTableCell cell : row.getTableCells()) {
                StringBuilder cellContent = new StringBuilder();
                for (IBodyElement elem : cell.getBodyElements()) {
                    if (elem.getElementType() == BodyElementType.PARAGRAPH) {
                        XWPFParagraph p = (XWPFParagraph) elem;
                        String text = extractParagraphText(p);
                        if (text != null && !text.isBlank()) {
                            cellContent.append(text.trim()).append("<br>");
                        }
                    } else if (elem.getElementType() == BodyElementType.TABLE) {
                        cellContent.append(flattenTableText((XWPFTable) elem).replace("\n", "<br>")).append("<br>");
                    }
                }
                String cellText = cellContent.toString().trim();
                if (cellText.endsWith("<br>")) {
                    cellText = cellText.substring(0, cellText.length() - 4);
                }
                
                // Keep placeholder for empty cell to preserve table shape.
                cellTexts.add(cellText);
            }

            // Skip rows that are truly empty (all cells blank).
            boolean hasAnyText = cellTexts.stream().anyMatch(text -> !text.isBlank());
            if (!hasAnyText) continue;

            sb.append(String.join(" | ", cellTexts)).append("\n");
        }

        return sb.toString().trim();
    }

    /**
     * Extracts paragraph text while preserving explicit line breaks.
     * Converts soft breaks inside a paragraph to <br> to keep Word formatting.
     */
    private String extractParagraphText(XWPFParagraph para) {
        if (para == null) return "";
        StringBuilder sb = new StringBuilder();
        List<XWPFRun> runs = para.getRuns();
        if (runs == null || runs.isEmpty()) {
            String fallback = para.getText();
            return fallback != null ? fallback.trim() : "";
        }

        for (XWPFRun run : runs) {
            String text = run.text();
            if (text != null && !text.isBlank()) {
                sb.append(text);
            }

            if (run.getCTR() != null) {
                int brCount = run.getCTR().sizeOfBrArray();
                int crCount = run.getCTR().sizeOfCrArray();
                int tabCount = run.getCTR().sizeOfTabArray();

                for (int i = 0; i < brCount + crCount; i++) {
                    sb.append("<br>");
                }
                for (int i = 0; i < tabCount; i++) {
                    sb.append("\t");
                }
            }
        }

        String result = sb.toString().trim();
        return result.replace("\r\n", "<br>")
                .replace("\n", "<br>")
                .replace("\r", "<br>");
    }

    /**
     * Extracts the first score value from a paragraph text using regex.
     * Handles both "X marks" and "marks: X" patterns, and comma/dot decimals.
     */
    private BigDecimal extractScore(String text) {
        Matcher m = SCORE_PATTERN.matcher(text);
        if (m.find()) {
            String raw = m.group(1) != null ? m.group(1) : m.group(2);
            raw = raw.replace(",", "."); // handle comma decimals
            try {
                return new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    // ─── Test-Case File Parser ────────────────────────────────────────────────

    private record TcFileContent(
            List<ParsedExamPaper.ParsedTestCase> testCases,
            boolean removeSpaces,
            boolean caseSensitive
    ) {}

    /**
     * Parses a {@code tc{n}.txt} file.
     *
     * <p>Format: Zero or more {@code INPUT:}/{@code OUTPUT:} pairs, followed by optional
     * {@code REMOVE_SPACES:} and {@code CASE_SENSITIVE:} flag blocks at the end of file.</p>
     *
     * <p>Whitespace-only lines inside an INPUT or OUTPUT block are preserved.
     * Trailing blank lines at the end of a block are trimmed.</p>
     */
    private TcFileContent parseTcFile(byte[] tcBytes, int qNum, BigDecimal maxScore) {
        String content = new String(tcBytes, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace("\r", "\n");

        String[] lines = content.split("\n", -1);

        List<ParsedExamPaper.ParsedTestCase> testCases = new ArrayList<>();
        boolean removeSpaces = true;   // default
        boolean caseSensitive = true;  // default

        // State machine
        String state = "IDLE"; // IDLE, IN_INPUT, IN_OUTPUT
        StringBuilder inputBuf = new StringBuilder();
        StringBuilder outputBuf = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String trimmed = raw.trim();

            switch (trimmed.toUpperCase()) {
                case "INPUT:" -> {
                    if ("IN_INPUT".equals(state)) {
                        throw missingOutputException(qNum, i + 1);
                    }
                    // Flush previous complete test case if we were in OUTPUT state
                    if ("IN_OUTPUT".equals(state)) {
                        testCases.add(buildTestCase(testCases.size() + 1,
                                inputBuf, outputBuf, qNum, maxScore));
                        inputBuf.setLength(0);
                        outputBuf.setLength(0);
                    }
                    state = "IN_INPUT";
                }
                case "OUTPUT:" -> {
                    if (!"IN_INPUT".equals(state)) {
                        throw new InvalidZipStructureException(
                                "Câu " + qNum + " dòng " + (i + 1) +
                                ": Gặp 'OUTPUT:' mà không có 'INPUT:' trước đó trong tc" + qNum + ".txt.");
                    }
                    state = "IN_OUTPUT";
                }
                case "REMOVE_SPACES:" -> {
                    if ("IN_INPUT".equals(state)) {
                        throw missingOutputException(qNum, i + 1);
                    }
                    // Flush last test case
                    if ("IN_OUTPUT".equals(state)) {
                        testCases.add(buildTestCase(testCases.size() + 1,
                                inputBuf, outputBuf, qNum, maxScore));
                        inputBuf.setLength(0);
                        outputBuf.setLength(0);
                    }
                    state = "IN_REMOVE_SPACES";
                }
                case "CASE_SENSITIVE:" -> {
                    if ("IN_INPUT".equals(state)) {
                        throw missingOutputException(qNum, i + 1);
                    }
                    if ("IN_OUTPUT".equals(state)) {
                        testCases.add(buildTestCase(testCases.size() + 1,
                                inputBuf, outputBuf, qNum, maxScore));
                        inputBuf.setLength(0);
                        outputBuf.setLength(0);
                    }
                    state = "IN_CASE_SENSITIVE";
                }
                case "YES", "NO" -> {
                    if ("IN_REMOVE_SPACES".equals(state)) {
                        removeSpaces = trimmed.equalsIgnoreCase("YES");
                        state = "IDLE";
                    } else if ("IN_CASE_SENSITIVE".equals(state)) {
                        caseSensitive = trimmed.equalsIgnoreCase("YES");
                        state = "IDLE";
                    } else {
                        appendLine(state, raw, inputBuf, outputBuf);
                    }
                }
                default -> {
                    appendLine(state, raw, inputBuf, outputBuf);
                }
            }
        }

        if ("IN_INPUT".equals(state)) {
            throw missingOutputException(qNum, lines.length);
        }

        // Flush the last test case if the file ended without flags.
        if ("IN_OUTPUT".equals(state)) {
            testCases.add(buildTestCase(testCases.size() + 1,
                    inputBuf, outputBuf, qNum, maxScore));
        }

        // Validation
        if (testCases.isEmpty()) {
            throw new InvalidZipStructureException(
                    "Câu " + qNum + ": File tc" + qNum + ".txt không có cặp INPUT/OUTPUT hợp lệ nào.");
        }

        return new TcFileContent(testCases, removeSpaces, caseSensitive);
    }

    private InvalidZipStructureException missingOutputException(int qNum, int lineNumber) {
        return new InvalidZipStructureException(
                "Cau " + qNum + " dong " + lineNumber +
                ": Gap 'INPUT:' nhung thieu 'OUTPUT:' tuong ung trong tc" + qNum + ".txt.");
    }

    private void appendLine(String state, String rawLine,
                            StringBuilder inputBuf, StringBuilder outputBuf) {
        if ("IN_INPUT".equals(state)) {
            if (inputBuf.length() > 0) inputBuf.append("\n");
            inputBuf.append(rawLine);
        } else if ("IN_OUTPUT".equals(state)) {
            if (outputBuf.length() > 0) outputBuf.append("\n");
            outputBuf.append(rawLine);
        }
        // IDLE / flag states: ignore
    }

    private ParsedExamPaper.ParsedTestCase buildTestCase(
            int tcNumber,
            StringBuilder inputBuf,
            StringBuilder outputBuf,
            int qNum,
            BigDecimal maxScore) {

        String outputStr = outputBuf.toString().trim();
        if (outputStr.isEmpty()) {
            throw new InvalidZipStructureException(
                    "Câu " + qNum + " test case " + tcNumber +
                    ": Block OUTPUT rỗng trong tc" + qNum + ".txt. Mỗi test case phải có expected output.");
        }

        String inputStr = inputBuf.toString().trim();

        // Score will be recalculated once we know totalTC; use maxScore as placeholder
        // The actual score is set in ExamPaperServiceImpl after all TCs are parsed
        return new ParsedExamPaper.ParsedTestCase(
                tcNumber,
                inputStr.isEmpty() ? null : inputStr,
                outputStr,
                maxScore // placeholder — will be divided by totalTC in service layer
        );
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    /**
     * Case-insensitive search for an entry whose path starts with {@code prefix}
     * and whose file name (last path segment) equals {@code filename} (case-insensitive).
     */
    private String findEntryKey(Map<String, byte[]> entries, String prefix, String filename) {
        for (String key : entries.keySet()) {
            if (key.startsWith(prefix)) {
                String segment = key.substring(prefix.length());
                // filename should be directly in this folder (no deeper sub-path)
                if (!segment.contains("/") && segment.equalsIgnoreCase(filename)) {
                    return key;
                }
            }
        }
        return null;
    }

    /** Normalizes backslashes to forward slashes (Windows zip entries can use backslashes). */
    private String normalizeSlash(String path) {
        return path.replace('\\', '/');
    }
}
