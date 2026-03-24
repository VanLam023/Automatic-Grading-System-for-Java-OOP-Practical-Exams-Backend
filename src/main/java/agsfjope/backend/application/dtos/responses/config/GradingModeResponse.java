package agsfjope.backend.application.dtos.responses.config;

import agsfjope.backend.core.enums.GradingMode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Response DTO for one grading mode configuration.
 */
@Data
@Builder
public class GradingModeResponse {

    /**
     * Grading mode enum.
     */
    private GradingMode mode;

    /**
     * Display name of grading mode.
     */
    private String displayName;

    /**
     * Test case score weight percentage.
     */
    private BigDecimal testCaseWeight;

    /**
     * OOP score weight percentage.
     */
    private BigDecimal oopWeight;

    /**
     * Whether mode only generates OOP comments.
     */
    private Boolean oopCommentOnly;

    /**
     * Whether submission fails if all test cases are zero.
     */
    private Boolean failIfZeroTestCase;

    /**
     * Whether submission fails if OOP rule is violated.
     */
    private Boolean failIfOopViolated;

    /**
     * Whether grading mode is active.
     */
    private Boolean isActive;

    /**
     * Optional description.
     */
    private String description;
}
