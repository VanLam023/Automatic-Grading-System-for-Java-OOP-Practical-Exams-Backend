package agsfjope.backend.infrastructure.storage.parser;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable result of parsing an exam paper archive (.zip or .rar).
 *
 * <p>Produced by {@link ZipExamPaperParser} and consumed by
 * {@code ExamPaperServiceImpl} to persist Questions and TestCases to the database.</p>
 */
public record ParsedExamPaper(List<ParsedQuestion> questions) {

    /** Total number of all test cases across all questions. */
    public int totalTestCases() {
        return questions.stream().mapToInt(q -> q.testCases().size()).sum();
    }

    // ─────────────────────────────────────────────────────────────────────

    /**
     * Parsed representation of one question folder (e.g., {@code 1/}, {@code 2/}).
     *
     * @param questionNumber sequential number parsed from the folder name
     * @param title          question title extracted from the {@code Q{n}.docx} document
     * @param description    full body text of the {@code Q{n}.docx} document (may be null)
     * @param maxScore       maximum score for this question, extracted from the .docx
     * @param removeSpaces   parsed from {@code REMOVE_SPACES} flag in {@code tc{n}.txt}
     * @param caseSensitive  parsed from {@code CASE_SENSITIVE} flag in {@code tc{n}.txt}
     * @param testCases      ordered list of test cases
     */
    public record ParsedQuestion(
            int questionNumber,
            String title,
            String description,
            BigDecimal maxScore,
            boolean removeSpaces,
            boolean caseSensitive,
            List<ParsedTestCase> testCases
    ) {}

    // ─────────────────────────────────────────────────────────────────────

    /**
     * Parsed representation of one {@code INPUT:} / {@code OUTPUT:} block
     * inside a {@code tc{n}.txt} file.
     *
     * @param testCaseNumber sequential order (1-based) within the question
     * @param inputData      raw multi-line input string (may be null if INPUT block is empty)
     * @param expectedOutput raw multi-line expected output string
     * @param score          score awarded if this TC passes = {@code maxScore / totalTCs}
     */
    public record ParsedTestCase(
            int testCaseNumber,
            String inputData,
            String expectedOutput,
            BigDecimal score
    ) {}
}
