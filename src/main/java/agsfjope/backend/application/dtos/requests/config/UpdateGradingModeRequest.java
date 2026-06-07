package agsfjope.backend.application.dtos.requests.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO for updating a single grading mode configuration.
 */
@Data
public class UpdateGradingModeRequest {

    /**
     * Human-readable display name for grading mode.
     */
    @NotBlank(message = "Display name không được để trống")
    private String displayName;

    /**
     * Weight (%) for test case score component.
     */
    @NotNull(message = "Test case weight không được để trống")
    @DecimalMin(value = "0.00", message = "Test case weight phải từ 0 đến 100")
    @DecimalMax(value = "100.00", message = "Test case weight phải từ 0 đến 100")
    private BigDecimal testCaseWeight;

    /**
     * Weight (%) for OOP score component.
     */
    @NotNull(message = "OOP weight không được để trống")
    @DecimalMin(value = "0.00", message = "OOP weight phải từ 0 đến 100")
    @DecimalMax(value = "100.00", message = "OOP weight phải từ 0 đến 100")
    private BigDecimal oopWeight;

    /**
     * Flag indicating OOP comments only mode.
     */
    @NotNull(message = "oopCommentOnly không được để trống")
    private Boolean oopCommentOnly;

    /**
     * Flag to fail submission when all test cases score zero.
     */
    @NotNull(message = "failIfZeroTestCase không được để trống")
    private Boolean failIfZeroTestCase;

    /**
     * Flag to fail submission when OOP violation is detected.
     */
    @NotNull(message = "failIfOopViolated không được để trống")
    private Boolean failIfOopViolated;

    /**
     * Whether this grading mode is active.
     */
    @NotNull(message = "isActive không được để trống")
    private Boolean isActive;

    /**
     * Optional grading mode description.
     */
    private String description;

    /**
     * Cross-field validation to ensure total weight = 100.
     *
     * @return true when testCaseWeight + oopWeight equals 100
     */
    @AssertTrue(message = "MSG-80: Tổng trọng số TestCaseWeight + OopWeight phải bằng 100")
    public boolean isWeightSumValid() {
        if (testCaseWeight == null || oopWeight == null) {
            return true;
        }
        return testCaseWeight.add(oopWeight).compareTo(new BigDecimal("100")) == 0;
    }

    /**
     * Cross-field validation to ensure both oopCommentOnly and failIfOopViolated are not enabled at the same time.
     *
     * @return true when not both are true
     */
    @AssertTrue(message = "Không thể đồng thời bật 'Chỉ nhận xét OOP' và 'Rớt nếu vi phạm OOP'")
    public boolean isOopRulesValid() {
        if (oopCommentOnly == null || failIfOopViolated == null) {
            return true;
        }
        return !(oopCommentOnly && failIfOopViolated);
    }
}
