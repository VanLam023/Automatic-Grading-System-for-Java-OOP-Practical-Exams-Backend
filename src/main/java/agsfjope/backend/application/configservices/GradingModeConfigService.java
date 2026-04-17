package agsfjope.backend.application.configservices;

import agsfjope.backend.application.dtos.requests.config.CreateGradingModeRequest;
import agsfjope.backend.application.dtos.requests.config.UpdateGradingModeRequest;
import agsfjope.backend.application.dtos.responses.config.GradingModeListResponse;
import agsfjope.backend.application.dtos.responses.config.GradingModeResponse;
import agsfjope.backend.core.enums.GradingMode;

/**
 * Service interface for grading mode configuration use cases.
 */
public interface GradingModeConfigService {

    /**
     * Get all grading mode configurations and current default mode.
     *
     * @return list response
     */
    GradingModeListResponse getAllGradingModes();

    /**
     * Get one grading mode configuration by enum mode.
     *
     * @param mode grading mode enum
     * @return grading mode detail
     */
    GradingModeResponse getGradingModeDetail(GradingMode mode);

    /**
     * Create one grading mode configuration.
     *
     * @param request create payload
     */
    void createGradingMode(CreateGradingModeRequest request);

    /**
     * Update one grading mode configuration.
     *
     * @param mode    grading mode enum
     * @param request update payload
     */
    void updateGradingMode(GradingMode mode, UpdateGradingModeRequest request);

    /**
     * Set default grading mode in system configuration.
     *
     * @param mode              target default grading mode
     * @param updatedByUsername username performing the update
     */
    void setDefaultGradingMode(GradingMode mode, String updatedByUsername);
}
