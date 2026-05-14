package agsfjope.backend.application.dtos.responses.grading;

import agsfjope.backend.core.enums.TestCaseStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Detail of a single test case execution result — included inside {@link AnswerGradingDetail}.
 */
@Data
@Builder
public class TestCaseResultDetail {

    private UUID testCaseResultId;
    private int testCaseNumber;

    /** Status: PASS_TESTCASE, FAIL_TESTCASE, ERROR, TIMEOUT. */
    private TestCaseStatus status;

    /** Expected output from the exam paper (what correct answer should produce). */
    private String expectedOutput;

    /** Actual output produced by the student's JAR (may be truncated for display). */
    private String actualOutput;

    /** Execution time in milliseconds. */
    private Integer executionTimeMs;

    /**
     * Error or violation message — shown to student.
     * E.g.: "Runtime error: NullPointerException at Main.java:12"
     *        "TIMEOUT: exceeded 5000ms"
     *        "TAMPERED: Modified exam class files detected"
     */
    private String errorMessage;

    /** Score earned for this test case. */
    private BigDecimal scoreEarned;
}
