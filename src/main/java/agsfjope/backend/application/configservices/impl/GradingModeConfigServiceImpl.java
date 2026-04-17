package agsfjope.backend.application.configservices.impl;

import agsfjope.backend.application.configservices.GradingModeConfigService;
import agsfjope.backend.application.dtos.requests.config.CreateGradingModeRequest;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import agsfjope.backend.infrastructure.audit.Auditable;
import agsfjope.backend.core.enums.AuditAction;

import java.math.BigDecimal;
import java.util.List;

/**
 * Implementation of grading mode configuration use cases.
 */
@Service
@RequiredArgsConstructor
public class GradingModeConfigServiceImpl implements GradingModeConfigService {

    private static final String DEFAULT_GRADING_MODE_KEY = "DEFAULT_GRADING_MODE";

    private final GradingModeConfigRepository gradingModeConfigRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final UserRepository userRepository;

    @Override
    public GradingModeListResponse getAllGradingModes() {
        List<GradingModeResponse> modes = gradingModeConfigRepository.findAllByOrderByModeAsc()
                .stream()
                .map(this::toResponse)
                .toList();

        String defaultMode = systemConfigRepository.findByConfigKey(DEFAULT_GRADING_MODE_KEY)
                .map(SystemConfig::getConfigValue)
                .orElseThrow(() -> new ConfigNotFoundException("Không tìm thấy cấu hình DEFAULT_GRADING_MODE"));

        return GradingModeListResponse.builder()
                .modes(modes)
                .defaultMode(defaultMode)
                .build();
    }

    @Override
    public GradingModeResponse getGradingModeDetail(GradingMode mode) {
        GradingModeConfig config = gradingModeConfigRepository.findByMode(mode)
                .orElseThrow(() -> new ConfigNotFoundException("Không tìm thấy cấu hình Grading Mode: " + mode));
        return toResponse(config);
    }

        @Override
        @Transactional
        @Auditable(action = AuditAction.CREATE, entityType = "GRADING_MODE_CONFIG")
        public void createGradingMode(CreateGradingModeRequest request) {
                BigDecimal total = request.getTestCaseWeight().add(request.getOopWeight());
                if (total.compareTo(new BigDecimal("100")) != 0) {
                        throw new InvalidConfigException("MSG-80: Tổng trọng số TestCaseWeight + OopWeight phải bằng 100");
                }

                gradingModeConfigRepository.findByMode(request.getMode())
                                .ifPresent(existing -> {
                                        throw new InvalidConfigException("Grading Mode đã tồn tại: " + request.getMode());
                                });

                GradingModeConfig config = GradingModeConfig.builder()
                                .mode(request.getMode())
                                .displayName(request.getDisplayName())
                                .testCaseWeight(request.getTestCaseWeight())
                                .oopWeight(request.getOopWeight())
                                .oopCommentOnly(request.getOopCommentOnly())
                                .failIfZeroTestCase(request.getFailIfZeroTestCase())
                                .failIfOopViolated(request.getFailIfOopViolated())
                                .isActive(request.getIsActive())
                                .description(request.getDescription())
                                .build();

                gradingModeConfigRepository.save(config);
        }

    @Override
    @Transactional
    @Auditable(action = AuditAction.UPDATE, entityType = "GRADING_MODE_CONFIG")
    public void updateGradingMode(GradingMode mode, UpdateGradingModeRequest request) {
        // Service-level validation (the same rule also exists in DTO with @AssertTrue)
        BigDecimal total = request.getTestCaseWeight().add(request.getOopWeight());
        if (total.compareTo(new BigDecimal("100")) != 0) {
            throw new InvalidConfigException("MSG-80: Tổng trọng số TestCaseWeight + OopWeight phải bằng 100");
        }

        GradingModeConfig config = gradingModeConfigRepository.findByMode(mode)
                .orElseThrow(() -> new ConfigNotFoundException("Không tìm thấy cấu hình Grading Mode: " + mode));

        config.setDisplayName(request.getDisplayName());
        config.setTestCaseWeight(request.getTestCaseWeight());
        config.setOopWeight(request.getOopWeight());
        config.setOopCommentOnly(request.getOopCommentOnly());
        config.setFailIfZeroTestCase(request.getFailIfZeroTestCase());
        config.setFailIfOopViolated(request.getFailIfOopViolated());
        config.setIsActive(request.getIsActive());
        config.setDescription(request.getDescription());

        gradingModeConfigRepository.save(config);
    }

    @Override
    @Transactional
    @Auditable(action = AuditAction.UPDATE, entityType = "GRADING_MODE_CONFIG")
    public void setDefaultGradingMode(GradingMode mode, String updatedByUsername) {
        // Ensure selected mode exists before setting default
        gradingModeConfigRepository.findByMode(mode)
                .orElseThrow(() -> new ConfigNotFoundException("Không tìm thấy cấu hình Grading Mode: " + mode));

        User updatedBy = userRepository.findByUsername(updatedByUsername)
                .orElseThrow(() -> new ConfigNotFoundException("Không tìm thấy người dùng cập nhật: " + updatedByUsername));

        SystemConfig defaultConfig = systemConfigRepository.findByConfigKey(DEFAULT_GRADING_MODE_KEY)
                .orElseThrow(() -> new ConfigNotFoundException("Không tìm thấy cấu hình DEFAULT_GRADING_MODE"));

        defaultConfig.setConfigValue(mode.name());
        defaultConfig.setIsEncrypted(false);
        defaultConfig.setUpdatedBy(updatedBy);
        systemConfigRepository.save(defaultConfig);
    }

    private GradingModeResponse toResponse(GradingModeConfig config) {
        return GradingModeResponse.builder()
                .mode(config.getMode())
                .displayName(config.getDisplayName())
                .testCaseWeight(config.getTestCaseWeight())
                .oopWeight(config.getOopWeight())
                .oopCommentOnly(config.getOopCommentOnly())
                .failIfZeroTestCase(config.getFailIfZeroTestCase())
                .failIfOopViolated(config.getFailIfOopViolated())
                .isActive(config.getIsActive())
                .description(config.getDescription())
                .build();
    }
}
