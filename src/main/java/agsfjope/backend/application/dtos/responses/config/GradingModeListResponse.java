package agsfjope.backend.application.dtos.responses.config;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response DTO containing all grading modes and current default mode.
 */
@Data
@Builder
public class GradingModeListResponse {

    /**
     * List of grading mode configuration details.
     */
    private List<GradingModeResponse> modes;

    /**
     * Name of default grading mode.
     */
    private String defaultMode;
}
