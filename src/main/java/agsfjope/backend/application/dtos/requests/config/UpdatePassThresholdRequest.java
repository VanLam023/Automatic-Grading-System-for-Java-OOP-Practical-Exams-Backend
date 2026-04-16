package agsfjope.backend.application.dtos.requests.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

/**
 * DTO for updating the grading pass threshold.
 *
 * <p>The pass threshold defines the minimum score a student must
 * <strong>exceed</strong> to be considered PASS after grading.
 * Default value is 0 (any score &gt; 0 passes).</p>
 */
@Data
public class UpdatePassThresholdRequest {

    /**
     * Minimum score to pass an exam.
     * The student's final score must be GREATER THAN this value.
     * Must be >= 0.
     */
    @NotNull(message = "Ngưỡng điểm đạt không được để trống")
    @DecimalMin(value = "0.0", message = "Ngưỡng điểm đạt phải >= 0")
    private BigDecimal passThreshold;
}
