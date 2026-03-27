package agsfjope.backend.application.configservices;

import agsfjope.backend.application.dtos.requests.config.TestAiConnectionRequest;
import agsfjope.backend.application.dtos.requests.config.TestEmailConnectionRequest;
import agsfjope.backend.application.dtos.requests.config.UpdateAiConfigRequest;
import agsfjope.backend.application.dtos.requests.config.UpdateEmailConfigRequest;
import agsfjope.backend.application.dtos.requests.config.UpdatePayosConfigRequest;
import agsfjope.backend.application.dtos.requests.config.UpdateSystemSettingsRequest;
import agsfjope.backend.application.dtos.responses.config.AiConfigResponse;
import agsfjope.backend.application.dtos.responses.config.PayosConfigResponse;
import agsfjope.backend.application.dtos.responses.config.SystemSettingsResponse;
import agsfjope.backend.application.dtos.responses.config.TestAiConnectionResponse;
import agsfjope.backend.application.dtos.responses.config.TestEmailConnectionResponse;

/**
 * Service interface for system-level key-value configuration management.
 */
public interface SystemConfigService {

    /**
     * Get AI configuration group.
     *
     * @return AI configuration response
     */
    AiConfigResponse getAiConfig();

    /**
     * Update AI configuration group.
     *
     * @param request           new AI config values
     * @param updatedByUsername username performing the update
     */
    void updateAiConfig(UpdateAiConfigRequest request, String updatedByUsername);

    /**
     * Test AI provider connection.
     *
     * @param request test payload
     * @return test result
     */
    TestAiConnectionResponse testAiConnection(TestAiConnectionRequest request);

    /**
     * Test SMTP email server connection.
     *
     * @param request test SMTP payload
     * @return test result
     */
    TestEmailConnectionResponse testEmailConnection(TestEmailConnectionRequest request);

    /**
     * Get PayOS configuration group.
     *
     * @return PayOS config response
     */
    PayosConfigResponse getPayosConfig();

    /**
     * Update PayOS configuration group.
     *
     * @param request           new PayOS values
     * @param updatedByUsername username performing the update
     */
    void updatePayosConfig(UpdatePayosConfigRequest request, String updatedByUsername);

    /**
     * Get system settings group.
     *
     * @return system settings response
     */
    SystemSettingsResponse getSystemSettings();

    /**
     * Update system settings group.
     *
     * @param request           new system settings
     * @param updatedByUsername username performing the update
     */
    void updateSystemSettings(UpdateSystemSettingsRequest request, String updatedByUsername);

    /**
     * Update SMTP email configuration.
     *
     * @param request           new SMTP email config values
     * @param updatedByUsername username performing the update
     */
    void updateEmailConfig(UpdateEmailConfigRequest request, String updatedByUsername);
}
