package agsfjope.backend.application.dtos.responses.exampaper;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for returning the details of a single test case within a question.
 *
 * <p>Each test case is parsed from one {@code INPUT:} / {@code OUTPUT:} block inside
 * the {@code tc{n}.txt} file. Score is computed as {@code maxScore / totalTestCases} (BR scoring rule).</p>
 *
 * <p>Note: {@code inputData} and {@code expectedOutput} may be long multi-line strings.
 * The API returns the full content — UI should truncate for display if needed.</p>
 */
@Data
@Builder
public class TestCaseResponse {

    /** Unique identifier of the test case. */
    private UUID testCaseId;

    /**
     * Sequential number of the test case within its question (1-based).
     * Reflects the order in which {@code INPUT:} blocks appear in {@code tc{n}.txt}.
     */
    private Integer testCaseNumber;

    /**
     * The raw input data (may be multi-line) that will be fed to the student's program via stdin.
     * If {@code removeSpaces = true}, all whitespace will be stripped before feeding during grading.
     */
    private String inputData;

    /** The expected output (may be multi-line) that the student's program should produce. */
    private String expectedOutput;

    /**
     * Score awarded if this test case passes.
     * Computed as {@code maxScore / totalTestCases} for the parent question.
     */
    private BigDecimal score;

    /**
     * Maximum execution time allowed for the student's program on this test case (in milliseconds).
     * Default: 5000ms (5 seconds). Exceeding this limit results in a TIMEOUT status.
     */
    private Integer timeLimitMs;
}
