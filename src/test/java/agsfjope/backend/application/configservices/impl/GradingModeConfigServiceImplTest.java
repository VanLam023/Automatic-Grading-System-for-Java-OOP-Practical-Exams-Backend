package agsfjope.backend.application.configservices.impl;

import agsfjope.backend.application.dtos.requests.config.UpdateGradingModeRequest;
import agsfjope.backend.application.dtos.responses.config.GradingModeListResponse;
import agsfjope.backend.application.dtos.responses.config.GradingModeResponse;
import agsfjope.backend.core.entities.GradingModeConfig;
import agsfjope.backend.core.entities.SystemConfig;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.GradingMode;
import agsfjope.backend.core.exceptions.config.ConfigNotFoundException;
import agsfjope.backend.core.exceptions.config.InvalidConfigException;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.config.GradingModeConfigRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.testutils.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho GradingModeConfigServiceImpl.
 * Phân loại: [N] Normal, [B] Boundary, [A] Abnormal.
 * Pattern: AAA (Arrange - Act - Assert).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GradingModeConfigServiceImpl Tests")
class GradingModeConfigServiceImplTest {

    @Mock
    private GradingModeConfigRepository gradingModeConfigRepository;

    @Mock
    private SystemConfigRepository systemConfigRepository;

    @Mock
    private UserRepository userRepository;

    @Mock private agsfjope.backend.infrastructure.audit.AuditLogHelper auditLogHelper;

    @InjectMocks
    private GradingModeConfigServiceImpl service;

    // ─── Shared test entities ─────────────────────────────────────────────────

    private GradingModeConfig mode1Config;
    private GradingModeConfig mode2Config;
    private SystemConfig defaultModeSystemConfig;
    private User adminUser;

    @BeforeEach
    void setUp() {
        mode1Config = GradingModeConfig.builder()
                .gradingModeConfigId(1)
                .mode(GradingMode.MODE_1)
                .displayName("TC 100% + OOP Guard")
                .testCaseWeight(new BigDecimal("100.00"))
                .oopWeight(new BigDecimal("0.00"))
                .oopCommentOnly(false)
                .failIfZeroTestCase(true)
                .failIfOopViolated(true)
                .isActive(true)
                .description("Chấm 100% dựa trên test cases")
                .build();

        mode2Config = GradingModeConfig.builder()
                .gradingModeConfigId(2)
                .mode(GradingMode.MODE_2)
                .displayName("TC 50% + AI 50%")
                .testCaseWeight(new BigDecimal("50.00"))
                .oopWeight(new BigDecimal("50.00"))
                .oopCommentOnly(false)
                .failIfZeroTestCase(false)
                .failIfOopViolated(false)
                .isActive(true)
                .description("Kết hợp TC và AI ngang bằng")
                .build();

        defaultModeSystemConfig = TestDataFactory.createPlainConfig("DEFAULT_GRADING_MODE", "MODE_1");
        adminUser = TestDataFactory.createActiveStudent(); // re-use lamtvse173173 as admin
    }

    // =========================================================================
    // getAllGradingModes()
    // =========================================================================

    @Test
    @DisplayName("[N] getAllGradingModes - Trả về list 2 mode + defaultMode='MODE_1' khi DB đủ dữ liệu")
    void getAllGradingModes_HappyPath_ReturnListWithDefault() {
        // Arrange
        when(gradingModeConfigRepository.findAllByOrderByModeAsc())
                .thenReturn(List.of(mode1Config, mode2Config));
        when(systemConfigRepository.findByConfigKey("DEFAULT_GRADING_MODE"))
                .thenReturn(Optional.of(defaultModeSystemConfig));

        // Act
        GradingModeListResponse response = service.getAllGradingModes();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getModes()).hasSize(2);
        assertThat(response.getDefaultMode()).isEqualTo("MODE_1");
        assertThat(response.getModes().get(0).getMode()).isEqualTo(GradingMode.MODE_1);
        verify(gradingModeConfigRepository).findAllByOrderByModeAsc();
        verify(systemConfigRepository).findByConfigKey("DEFAULT_GRADING_MODE");
    }

    @Test
    @DisplayName("[A] getAllGradingModes - Throw ConfigNotFoundException khi thiếu key DEFAULT_GRADING_MODE trong DB")
    void getAllGradingModes_MissingDefaultKey_ThrowConfigNotFoundException() {
        // Arrange
        when(gradingModeConfigRepository.findAllByOrderByModeAsc())
                .thenReturn(List.of(mode1Config));
        when(systemConfigRepository.findByConfigKey("DEFAULT_GRADING_MODE"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.getAllGradingModes())
                .isInstanceOf(ConfigNotFoundException.class)
                .hasMessageContaining("Không tìm thấy cấu hình DEFAULT_GRADING_MODE");
        verify(gradingModeConfigRepository).findAllByOrderByModeAsc();
    }

    @Test
    @DisplayName("[B] getAllGradingModes - Trả về list rỗng + defaultMode khi không có grading mode nào trong DB (Boundary)")
    void getAllGradingModes_EmptyModeList_ReturnEmptyListWithDefault() {
        // Arrange
        when(gradingModeConfigRepository.findAllByOrderByModeAsc())
                .thenReturn(List.of());
        when(systemConfigRepository.findByConfigKey("DEFAULT_GRADING_MODE"))
                .thenReturn(Optional.of(defaultModeSystemConfig));

        // Act
        GradingModeListResponse response = service.getAllGradingModes();

        // Assert
        assertThat(response.getModes()).isEmpty();
        assertThat(response.getDefaultMode()).isEqualTo("MODE_1");
    }

    // =========================================================================
    // getGradingModeDetail()
    // =========================================================================

    @Test
    @DisplayName("[N] getGradingModeDetail - Trả về GradingModeResponse đúng cho MODE_1")
    void getGradingModeDetail_ExistingMode_ReturnResponse() {
        // Arrange
        when(gradingModeConfigRepository.findByMode(GradingMode.MODE_1))
                .thenReturn(Optional.of(mode1Config));

        // Act
        GradingModeResponse response = service.getGradingModeDetail(GradingMode.MODE_1);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getMode()).isEqualTo(GradingMode.MODE_1);
        assertThat(response.getDisplayName()).isEqualTo("TC 100% + OOP Guard");
        assertThat(response.getTestCaseWeight()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("[A] getGradingModeDetail - Throw ConfigNotFoundException khi mode không tồn tại trong DB")
    void getGradingModeDetail_NonExistentMode_ThrowConfigNotFoundException() {
        // Arrange
        when(gradingModeConfigRepository.findByMode(GradingMode.MODE_3))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.getGradingModeDetail(GradingMode.MODE_3))
                .isInstanceOf(ConfigNotFoundException.class)
                .hasMessageContaining("Không tìm thấy cấu hình Grading Mode: MODE_3");
    }

    // =========================================================================
    // updateGradingMode()
    // =========================================================================

    private UpdateGradingModeRequest buildValidRequest(BigDecimal tcWeight, BigDecimal oopWeight) {
        UpdateGradingModeRequest req = new UpdateGradingModeRequest();
        req.setDisplayName("TC " + tcWeight + "% + AI " + oopWeight + "%");
        req.setTestCaseWeight(tcWeight);
        req.setOopWeight(oopWeight);
        req.setOopCommentOnly(false);
        req.setFailIfZeroTestCase(false);
        req.setFailIfOopViolated(false);
        req.setIsActive(true);
        req.setDescription("Mô tả cập nhật");
        return req;
    }

    @Test
    @DisplayName("[N] updateGradingMode - Cập nhật MODE_2 với trọng số 60/40 hợp lệ, save được gọi đúng 1 lần")
    void updateGradingMode_ValidWeights_SaveSuccessfully() {
        // Arrange
        UpdateGradingModeRequest request = buildValidRequest(
                new BigDecimal("60.00"), new BigDecimal("40.00"));
        when(gradingModeConfigRepository.findByMode(GradingMode.MODE_2))
                .thenReturn(Optional.of(mode2Config));
        when(gradingModeConfigRepository.save(any(GradingModeConfig.class)))
                .thenReturn(mode2Config);

        // Act
        service.updateGradingMode(GradingMode.MODE_2, request);

        // Assert
        verify(gradingModeConfigRepository).save(mode2Config);
        assertThat(mode2Config.getTestCaseWeight()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(mode2Config.getOopWeight()).isEqualByComparingTo(new BigDecimal("40.00"));
    }

    @Test
    @DisplayName("[A] updateGradingMode - Throw InvalidConfigException khi testCaseWeight + oopWeight != 100 (ví dụ: 60 + 30 = 90)")
    void updateGradingMode_InvalidWeightSum_ThrowInvalidConfigException() {
        // Arrange — tổng trọng số 60 + 30 = 90 (sai quy tắc)
        UpdateGradingModeRequest request = buildValidRequest(
                new BigDecimal("60.00"), new BigDecimal("30.00"));

        // Act & Assert
        assertThatThrownBy(() -> service.updateGradingMode(GradingMode.MODE_2, request))
                .isInstanceOf(InvalidConfigException.class)
                .hasMessageContaining("MSG-80: Tổng trọng số TestCaseWeight + OopWeight phải bằng 100");

        verify(gradingModeConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] updateGradingMode - Throw ConfigNotFoundException khi mode không tồn tại trong DB")
    void updateGradingMode_ModeNotFound_ThrowConfigNotFoundException() {
        // Arrange — weight hợp lệ nhưng mode không tồn tại
        UpdateGradingModeRequest request = buildValidRequest(
                new BigDecimal("100.00"), new BigDecimal("0.00"));
        when(gradingModeConfigRepository.findByMode(GradingMode.MODE_4))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.updateGradingMode(GradingMode.MODE_4, request))
                .isInstanceOf(ConfigNotFoundException.class)
                .hasMessageContaining("Không tìm thấy cấu hình Grading Mode: MODE_4");

        verify(gradingModeConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("[B] updateGradingMode - Cập nhật thành công với trọng số biên 100/0 (Boundary)")
    void updateGradingMode_BoundaryWeights100And0_SaveSuccessfully() {
        // Arrange — boundary: TC 100%, OOP 0% — tổng vẫn đúng 100
        UpdateGradingModeRequest request = buildValidRequest(
                new BigDecimal("100.00"), new BigDecimal("0.00"));
        when(gradingModeConfigRepository.findByMode(GradingMode.MODE_1))
                .thenReturn(Optional.of(mode1Config));
        when(gradingModeConfigRepository.save(any(GradingModeConfig.class)))
                .thenReturn(mode1Config);

        // Act
        service.updateGradingMode(GradingMode.MODE_1, request);

        // Assert
        verify(gradingModeConfigRepository).save(mode1Config);
    }

    // =========================================================================
    // setDefaultGradingMode()
    // =========================================================================

    @Test
    @DisplayName("[N] setDefaultGradingMode - Đặt MODE_2 làm default thành công bởi 'lamtvse173173'")
    void setDefaultGradingMode_ValidModeAndUser_SaveSuccessfully() {
        // Arrange
        when(gradingModeConfigRepository.findByMode(GradingMode.MODE_2))
                .thenReturn(Optional.of(mode2Config));
        when(userRepository.findByUsername("lamtvse173173"))
                .thenReturn(Optional.of(adminUser));
        when(systemConfigRepository.findByConfigKey("DEFAULT_GRADING_MODE"))
                .thenReturn(Optional.of(defaultModeSystemConfig));
        when(systemConfigRepository.save(any(SystemConfig.class)))
                .thenReturn(defaultModeSystemConfig);

        // Act
        service.setDefaultGradingMode(GradingMode.MODE_2, "lamtvse173173");

        // Assert
        verify(systemConfigRepository).save(defaultModeSystemConfig);
        assertThat(defaultModeSystemConfig.getConfigValue()).isEqualTo("MODE_2");
        assertThat(defaultModeSystemConfig.getIsEncrypted()).isFalse();
    }

    @Test
    @DisplayName("[A] setDefaultGradingMode - Throw ConfigNotFoundException khi mode không tồn tại")
    void setDefaultGradingMode_ModeNotFound_ThrowConfigNotFoundException() {
        // Arrange
        when(gradingModeConfigRepository.findByMode(GradingMode.MODE_3))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.setDefaultGradingMode(GradingMode.MODE_3, "lamtvse173173"))
                .isInstanceOf(ConfigNotFoundException.class)
                .hasMessageContaining("Không tìm thấy cấu hình Grading Mode: MODE_3");

        verify(userRepository, never()).findByUsername(any());
        verify(systemConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] setDefaultGradingMode - Throw ConfigNotFoundException khi user 'unknownuser' không tồn tại")
    void setDefaultGradingMode_UserNotFound_ThrowConfigNotFoundException() {
        // Arrange
        when(gradingModeConfigRepository.findByMode(GradingMode.MODE_2))
                .thenReturn(Optional.of(mode2Config));
        when(userRepository.findByUsername("unknownuser"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.setDefaultGradingMode(GradingMode.MODE_2, "unknownuser"))
                .isInstanceOf(ConfigNotFoundException.class)
                .hasMessageContaining("Không tìm thấy người dùng cập nhật: unknownuser");

        verify(systemConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] setDefaultGradingMode - Throw ConfigNotFoundException khi key DEFAULT_GRADING_MODE không có trong DB")
    void setDefaultGradingMode_MissingDefaultKey_ThrowConfigNotFoundException() {
        // Arrange
        when(gradingModeConfigRepository.findByMode(GradingMode.MODE_2))
                .thenReturn(Optional.of(mode2Config));
        when(userRepository.findByUsername("lamtvse173173"))
                .thenReturn(Optional.of(adminUser));
        when(systemConfigRepository.findByConfigKey("DEFAULT_GRADING_MODE"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.setDefaultGradingMode(GradingMode.MODE_2, "lamtvse173173"))
                .isInstanceOf(ConfigNotFoundException.class)
                .hasMessageContaining("Không tìm thấy cấu hình DEFAULT_GRADING_MODE");

        verify(systemConfigRepository, never()).save(any());
    }
}
