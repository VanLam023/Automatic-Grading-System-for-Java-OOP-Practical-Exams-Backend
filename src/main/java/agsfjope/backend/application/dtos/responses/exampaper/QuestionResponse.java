package agsfjope.backend.application.dtos.responses.exampaper;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTO for returning the details of a single question within an exam paper.
 *
 * <p>Each question is parsed from a numbered folder (e.g., {@code 1/}, {@code 2/}) inside
 * the exam paper archive. {@code maxScore} comes from the {@code Q{n}.docx} file.
 * {@code testCases} comes from the {@code tc{n}.txt} file.</p>
 */
@Data
@Builder
public class QuestionResponse {

    /** Unique identifier of the question. */
    private UUID questionId;

    /**
     * Sequential number of the question as found in the archive (1, 2, 3, ...).
     * Corresponds to the folder name inside the exam paper zip/rar.
     */
    private Integer questionNumber;

    /**
     * Title / name of the question, extracted from the {@code Q{n}.docx} file
     * (first non-empty paragraph treated as the title).
     */
    private String title;

    /** Optional description extracted from the {@code Q{n}.docx} document body. */
    private String description;

    /**
     * Maximum score for this question.
     * Parsed from the {@code Q{n}.docx} file. Each passing test case contributes
     * {@code maxScore / totalTestCases} points to the student's score.
     */
    private BigDecimal maxScore;

    /** Total number of test cases defined for this question. */
    private Integer totalTestCases;

    /**
     * If true, all whitespace is stripped from INPUT data before feeding to student's program.
     * Parsed from {@code REMOVE_SPACES} flag in {@code tc{n}.txt}.
     */
    private Boolean removeSpaces;

    /**
     * If true, output comparison between student result and expected output is case-sensitive.
     * Parsed from {@code CASE_SENSITIVE} flag in {@code tc{n}.txt}.
     */
    private Boolean caseSensitive;

    /** Ordered list of test cases for this question. */
    private List<TestCaseResponse> testCases;
}
