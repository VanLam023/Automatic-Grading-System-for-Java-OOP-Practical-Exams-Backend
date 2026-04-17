package agsfjope.backend.application.dtos.requests.config;

import agsfjope.backend.core.enums.GradingMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * DTO for creating a new grading mode configuration.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreateGradingModeRequest extends UpdateGradingModeRequest {

    /**
     * Target grading mode enum to create.
     */
    @NotNull(message = "Mode không được để trống")
    private GradingMode mode;
}
