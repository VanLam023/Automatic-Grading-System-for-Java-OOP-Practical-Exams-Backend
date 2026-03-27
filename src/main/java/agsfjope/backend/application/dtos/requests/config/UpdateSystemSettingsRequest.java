package agsfjope.backend.application.dtos.requests.config;

import agsfjope.backend.core.enums.GradingMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for updating general system settings (upload limits and default grading mode).
 * SMTP email configuration is managed separately via UpdateEmailConfigRequest.
 */
@Data
public class UpdateSystemSettingsRequest {

    /**
     * Maximum upload size for submission files (MB).
     */
    @NotNull(message = "Max upload size không được để trống")
    @Min(value = 1, message = "Max upload size phải lớn hơn hoặc bằng 1")
    private Integer maxUploadSizeMb;

    /**
     * Maximum exam paper package size (MB).
     */
    @NotNull(message = "Max exam paper size không được để trống")
    @Min(value = 1, message = "Max exam paper size phải lớn hơn hoặc bằng 1")
    private Integer maxExamPaperMb;

    /**
     * Default grading mode to apply system-wide.
     */
    @NotNull(message = "Default grading mode không được để trống")
    private GradingMode defaultGradingMode;
}
