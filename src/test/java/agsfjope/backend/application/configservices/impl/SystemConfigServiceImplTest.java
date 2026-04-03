package agsfjope.backend.application.configservices.impl;

import agsfjope.backend.application.dtos.requests.config.*;
import agsfjope.backend.application.dtos.responses.config.*;
import agsfjope.backend.core.entities.SystemConfig;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.GradingMode;
import agsfjope.backend.core.exceptions.config.ConfigNotFoundException;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.infrastructure.security.AesEncryptionUtil;
import agsfjope.backend.testutils.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests cho SystemConfigServiceImpl — 22 test cases (N/A/B).
 * Pattern: AAA (Arrange - Act - Assert)
 * Tên method: methodName_Condition_ExpectedBehavior
 */
@ExtendWith(MockitoExtension.class)
class SystemConfigServiceImplTest {

    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private UserRepository userRepository;
    @Mock private AesEncryptionUtil aesEncryptionUtil;

    @InjectMocks
    private SystemConfigServiceImpl systemConfigService;

    // =========================================================================
    // getAiConfig()  — [N] 1 normal, [A] 1 abnormal
    // =========================================================================

    @Test
    @DisplayName("[N] getAiConfig - Trả về AI config với API key được mask thành công")
    void getAiConfig_AllKeysExist_ReturnsAiConfigResponse() {
        // ── Arrange ──────────────────────────────────────────────────────────
        List<SystemConfig> configs = TestDataFactory.createAiConfigList();
        when(systemConfigRepository.findByConfigKeyIn(anyList())).thenReturn(configs);
        when(aesEncryptionUtil.decrypt("ENCRYPTED_AI_KEY")).thenReturn("raw-api-key");
        when(aesEncryptionUtil.mask("raw-api-key")).thenReturn("raw-****-key");

        // ── Act ───────────────────────────────────────────────────────────────
        AiConfigResponse response = systemConfigService.getAiConfig();

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals("gemini", response.getProvider());
        assertEquals("gemini-1.5-pro", response.getModel());
        assertEquals("raw-****-key", response.getApiKeyMasked());
        assertEquals("Vietnamese", response.getLanguage());
    }

    @Test
    @DisplayName("[A] getAiConfig - Thiếu key AI_PROVIDER trong DB → ConfigNotFoundException")
    void getAiConfig_MissingKey_ThrowsConfigNotFoundException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        // Trả về list rỗng → thiếu tất cả keys
        when(systemConfigRepository.findByConfigKeyIn(anyList())).thenReturn(List.of());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(ConfigNotFoundException.class, () -> systemConfigService.getAiConfig());
    }

    // =========================================================================
    // updateAiConfig()  — [N] 1 normal, [A] 1 abnormal, [B] 1 boundary
    // =========================================================================

    @Test
    @DisplayName("[N] updateAiConfig - Cập nhật đầy đủ provider, model, apiKey, language thành công")
    void updateAiConfig_FullRequest_SavesAllConfigs() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User admin = TestDataFactory.createActiveStudent();
        UpdateAiConfigRequest request = new UpdateAiConfigRequest();
        request.setProvider("openai");
        request.setModel("gpt-4o");
        request.setApiKey("sk-new-key");
        request.setLanguage("English");

        when(userRepository.findByUsername(admin.getUsername())).thenReturn(Optional.of(admin));
        when(systemConfigRepository.findByConfigKey(anyString()))
                .thenReturn(Optional.of(TestDataFactory.createPlainConfig("AI_PROVIDER", "gemini")));
        when(aesEncryptionUtil.encrypt("sk-new-key")).thenReturn("ENCRYPTED_NEW_KEY");
        when(systemConfigRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertDoesNotThrow(() -> systemConfigService.updateAiConfig(request, admin.getUsername()));
        // Provider, Model, ApiKey, Language → 4 lần save
        verify(systemConfigRepository, times(4)).save(any(SystemConfig.class));
    }

    @Test
    @DisplayName("[A] updateAiConfig - User thực hiện update không tồn tại → ConfigNotFoundException")
    void updateAiConfig_UserNotFound_ThrowsConfigNotFoundException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        UpdateAiConfigRequest request = new UpdateAiConfigRequest();
        request.setProvider("openai");
        request.setModel("gpt-4o");
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(ConfigNotFoundException.class,
                () -> systemConfigService.updateAiConfig(request, "ghost"));
        verify(systemConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("[B] updateAiConfig - apiKey là blank (chuỗi trắng) → KHÔNG save AI_API_KEY")
    void updateAiConfig_BlankApiKey_SkipsApiKeySave() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User admin = TestDataFactory.createActiveStudent();
        UpdateAiConfigRequest request = new UpdateAiConfigRequest();
        request.setProvider("gemini");
        request.setModel("gemini-pro");
        request.setApiKey("   ");   // Blank — phải bỏ qua

        when(userRepository.findByUsername(admin.getUsername())).thenReturn(Optional.of(admin));
        when(systemConfigRepository.findByConfigKey(anyString()))
                .thenReturn(Optional.of(TestDataFactory.createPlainConfig("AI_PROVIDER", "old")));
        when(systemConfigRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        // ── Act ───────────────────────────────────────────────────────────────
        assertDoesNotThrow(() -> systemConfigService.updateAiConfig(request, admin.getUsername()));

        // ── Assert ────────────────────────────────────────────────────────────
        // Chỉ save provider + model (2 lần), không save API_KEY
        verify(systemConfigRepository, times(2)).save(any(SystemConfig.class));
        verify(aesEncryptionUtil, never()).encrypt(anyString());
    }

    // =========================================================================
    // getPayosConfig()  — [N] 1 normal, [A] 1 abnormal
    // =========================================================================

    @Test
    @DisplayName("[N] getPayosConfig - Trả về PayOS config với các key được mask thành công")
    void getPayosConfig_AllKeysExist_ReturnsPayosConfigResponse() {
        // ── Arrange ──────────────────────────────────────────────────────────
        List<SystemConfig> configs = TestDataFactory.createPayosConfigList();
        when(systemConfigRepository.findByConfigKeyIn(anyList())).thenReturn(configs);
        when(aesEncryptionUtil.decrypt("ENCRYPTED_CLIENT_ID")).thenReturn("client-123");
        when(aesEncryptionUtil.decrypt("ENCRYPTED_PAYOS_KEY")).thenReturn("payos-key-xyz");
        when(aesEncryptionUtil.decrypt("ENCRYPTED_CHECKSUM")).thenReturn("checksum-abc");
        when(aesEncryptionUtil.mask(anyString())).thenReturn("****");

        // ── Act ───────────────────────────────────────────────────────────────
        PayosConfigResponse response = systemConfigService.getPayosConfig();

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals("****", response.getClientIdMasked());
        assertEquals("****", response.getApiKeyMasked());
        assertEquals("****", response.getChecksumKeyMasked());
        assertEquals(new BigDecimal("50000"), response.getAppealFee());
        assertEquals(15, response.getPaymentTimeoutMin());
    }

    @Test
    @DisplayName("[A] getPayosConfig - Thiếu key PAYOS_CLIENT_ID trong DB → ConfigNotFoundException")
    void getPayosConfig_MissingKey_ThrowsConfigNotFoundException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(systemConfigRepository.findByConfigKeyIn(anyList())).thenReturn(List.of());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(ConfigNotFoundException.class, () -> systemConfigService.getPayosConfig());
    }

    // =========================================================================
    // updatePayosConfig()  — [N] 1 normal, [A] 1 abnormal
    // =========================================================================

    @Test
    @DisplayName("[N] updatePayosConfig - Cập nhật toàn bộ PayOS config thành công, encrypt các key nhạy cảm")
    void updatePayosConfig_ValidRequest_SavesAllAndEncryptsSensitiveKeys() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User admin = TestDataFactory.createActiveStudent();
        UpdatePayosConfigRequest request = new UpdatePayosConfigRequest();
        request.setClientId("new-client-id");
        request.setApiKey("new-api-key");
        request.setChecksumKey("new-checksum");
        request.setAppealFee(new BigDecimal("75000"));
        request.setPaymentTimeoutMin(20);

        when(userRepository.findByUsername(admin.getUsername())).thenReturn(Optional.of(admin));
        when(systemConfigRepository.findByConfigKey(anyString()))
                .thenReturn(Optional.of(TestDataFactory.createPlainConfig("PAYOS_CLIENT_ID", "old")));
        when(aesEncryptionUtil.encrypt(anyString())).thenReturn("ENCRYPTED_VALUE");
        when(systemConfigRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertDoesNotThrow(() -> systemConfigService.updatePayosConfig(request, admin.getUsername()));
        // 5 keys: clientId, apiKey, checksum, appealFee, paymentTimeoutMin
        verify(systemConfigRepository, times(5)).save(any(SystemConfig.class));
        // clientId, apiKey, checksum phải được encrypt (3 lần)
        verify(aesEncryptionUtil, times(3)).encrypt(anyString());
    }

    @Test
    @DisplayName("[A] updatePayosConfig - User thực hiện update không tồn tại → ConfigNotFoundException")
    void updatePayosConfig_UserNotFound_ThrowsConfigNotFoundException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        UpdatePayosConfigRequest request = new UpdatePayosConfigRequest();
        request.setAppealFee(new BigDecimal("50000"));
        request.setPaymentTimeoutMin(15);
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(ConfigNotFoundException.class,
                () -> systemConfigService.updatePayosConfig(request, "ghost"));
    }

    // =========================================================================
    // getSystemSettings()  — [N] 1 normal, [A] 1 abnormal
    // =========================================================================

    @Test
    @DisplayName("[N] getSystemSettings - Trả về system settings đầy đủ, parse MODE_1 thành enum")
    void getSystemSettings_AllKeysExist_ReturnsSystemSettingsResponse() {
        // ── Arrange ──────────────────────────────────────────────────────────
        List<SystemConfig> configs = TestDataFactory.createSystemSettingsConfigList();
        when(systemConfigRepository.findByConfigKeyIn(anyList())).thenReturn(configs);

        // ── Act ───────────────────────────────────────────────────────────────
        SystemSettingsResponse response = systemConfigService.getSystemSettings();

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals(50, response.getMaxUploadSizeMb());
        assertEquals(10, response.getMaxExamPaperMb());
        assertEquals("smtp.gmail.com", response.getSmtpHost());
        assertEquals(587, response.getSmtpPort());
        assertEquals(GradingMode.MODE_1, response.getDefaultGradingMode());
        assertEquals(7, response.getAppealDeadlineDays());
    }

    @Test
    @DisplayName("[A] getSystemSettings - Thiếu key MAX_UPLOAD_SIZE_MB → ConfigNotFoundException")
    void getSystemSettings_MissingKey_ThrowsConfigNotFoundException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(systemConfigRepository.findByConfigKeyIn(anyList())).thenReturn(List.of());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(ConfigNotFoundException.class, () -> systemConfigService.getSystemSettings());
    }

    // =========================================================================
    // updateSystemSettings()  — [N] 1 normal, [A] 1 abnormal
    // =========================================================================

    @Test
    @DisplayName("[N] updateSystemSettings - Cập nhật upload limits và grading mode thành công")
    void updateSystemSettings_ValidRequest_SavesThreeConfigs() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User admin = TestDataFactory.createActiveStudent();
        UpdateSystemSettingsRequest request = new UpdateSystemSettingsRequest();
        request.setMaxUploadSizeMb(100);
        request.setMaxExamPaperMb(20);
        request.setDefaultGradingMode(GradingMode.MODE_2);

        when(userRepository.findByUsername(admin.getUsername())).thenReturn(Optional.of(admin));
        when(systemConfigRepository.findByConfigKey(anyString()))
                .thenReturn(Optional.of(TestDataFactory.createPlainConfig("MAX_UPLOAD_SIZE_MB", "50")));
        when(systemConfigRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertDoesNotThrow(() -> systemConfigService.updateSystemSettings(request, admin.getUsername()));
        // maxUploadSizeMb, maxExamPaperMb, defaultGradingMode → 3 lần save
        verify(systemConfigRepository, times(3)).save(any(SystemConfig.class));
    }

    @Test
    @DisplayName("[A] updateSystemSettings - User thực hiện update không tồn tại → ConfigNotFoundException")
    void updateSystemSettings_UserNotFound_ThrowsConfigNotFoundException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        UpdateSystemSettingsRequest request = new UpdateSystemSettingsRequest();
        request.setMaxUploadSizeMb(50);
        request.setMaxExamPaperMb(10);
        request.setDefaultGradingMode(GradingMode.MODE_1);
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(ConfigNotFoundException.class,
                () -> systemConfigService.updateSystemSettings(request, "ghost"));
    }

    // =========================================================================
    // updateEmailConfig()  — [N] 1 normal, [A] 1 abnormal
    // =========================================================================

    @Test
    @DisplayName("[N] updateEmailConfig - Cập nhật SMTP config thành công, encrypt username + password")
    void updateEmailConfig_ValidRequest_SavesFiveConfigsAndEncryptCredentials() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User admin = TestDataFactory.createActiveStudent();
        UpdateEmailConfigRequest request = new UpdateEmailConfigRequest();
        request.setSmtpHost("smtp.gmail.com");
        request.setSmtpPort(587);
        request.setSmtpUsername("user@gmail.com");
        request.setSmtpPassword("newPassword123");
        request.setSmtpFromEmail("noreply@fpt.edu.vn");

        when(userRepository.findByUsername(admin.getUsername())).thenReturn(Optional.of(admin));
        when(systemConfigRepository.findByConfigKey(anyString()))
                .thenReturn(Optional.of(TestDataFactory.createPlainConfig("SMTP_HOST", "old")));
        when(aesEncryptionUtil.encrypt(anyString())).thenReturn("ENCRYPTED_VALUE");
        when(systemConfigRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertDoesNotThrow(() -> systemConfigService.updateEmailConfig(request, admin.getUsername()));
        // host, port, username, password, fromEmail → 5 lần save
        verify(systemConfigRepository, times(5)).save(any(SystemConfig.class));
        // username + password được encrypt (2 lần)
        verify(aesEncryptionUtil, times(2)).encrypt(anyString());
    }

    @Test
    @DisplayName("[A] updateEmailConfig - User thực hiện update không tồn tại → ConfigNotFoundException")
    void updateEmailConfig_UserNotFound_ThrowsConfigNotFoundException() {
        // ── Arrange ──────────────────────────────────────────────────────────
        UpdateEmailConfigRequest request = new UpdateEmailConfigRequest();
        request.setSmtpHost("smtp.gmail.com");
        request.setSmtpPort(587);
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        // ── Act & Assert ──────────────────────────────────────────────────────
        assertThrows(ConfigNotFoundException.class,
                () -> systemConfigService.updateEmailConfig(request, "ghost"));
        verify(systemConfigRepository, never()).save(any());
    }

    // =========================================================================
    // testAiConnection()  — [N] 1 normal (provider không hợp lệ → return false), [A] 1 abnormal
    // =========================================================================

    @Test
    @DisplayName("[N] testAiConnection - Provider không được hỗ trợ → trả về response isConnected=false và errorMessage")
    void testAiConnection_UnsupportedProvider_ReturnsConnectedFalseWithError() {
        // ── Arrange ──────────────────────────────────────────────────────────
        TestAiConnectionRequest request = new TestAiConnectionRequest();
        request.setProvider("unknownprovider");
        request.setModel("some-model");
        request.setApiKey("some-key");

        // ── Act ───────────────────────────────────────────────────────────────
        TestAiConnectionResponse response = systemConfigService.testAiConnection(request);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertFalse(response.getIsConnected());
        assertNotNull(response.getErrorMessage());
        assertEquals("some-model", response.getModelName());
    }

    @Test
    @DisplayName("[A] testAiConnection - Provider null → trả về response isConnected=false (graceful, không throw exception)")
    void testAiConnection_NullProvider_ReturnsConnectedFalseGracefully() {
        // ── Arrange ──────────────────────────────────────────────────────────
        TestAiConnectionRequest request = new TestAiConnectionRequest();
        request.setProvider(null);      // null → normalize → "" → switch default branch → throw IAE → catch → false
        request.setModel("test-model");
        request.setApiKey("fake-key");

        // ── Act ───────────────────────────────────────────────────────────────
        // Service phải bắt exception và trả về response, KHÔNG throw ra ngoài
        TestAiConnectionResponse response = assertDoesNotThrow(
                () -> systemConfigService.testAiConnection(request)
        );

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertFalse(response.getIsConnected());
        assertNotNull(response.getErrorMessage());
        assertEquals("test-model", response.getModelName());
        assertTrue(response.getLatencyMs() >= 0);
    }

    // =========================================================================
    // testEmailConnection()  — [A] 1 abnormal (SMTP host sai → fail gracefully)
    // =========================================================================

    @Test
    @DisplayName("[A] testEmailConnection - SMTP host không tồn tại → trả về isConnected=false thay vì throw exception")
    void testEmailConnection_InvalidSmtpHost_ReturnsConnectedFalseGracefully() {
        // ── Arrange ──────────────────────────────────────────────────────────
        TestEmailConnectionRequest request = new TestEmailConnectionRequest();
        request.setSmtpHost("invalid.smtp.host.xyz");
        request.setSmtpPort(587);
        request.setSmtpUsername("user@test.com");
        request.setSmtpPassword("password");
        request.setSmtpFromEmail("from@test.com");
        request.setTestToEmail("to@test.com");

        // ── Act ───────────────────────────────────────────────────────────────
        TestEmailConnectionResponse response = systemConfigService.testEmailConnection(request);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        // Phải trả về false thay vì văng exception ra ngoài (graceful degradation)
        assertFalse(response.getIsConnected());
        assertNotNull(response.getErrorMessage());
        assertNotNull(response.getTestedAt());
        assertTrue(response.getLatencyMs() >= 0);
    }
}
