package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.configservices.GradingModeConfigService;
import agsfjope.backend.application.configservices.SystemConfigService;
import agsfjope.backend.application.dtos.requests.config.TestAiConnectionRequest;
import agsfjope.backend.application.dtos.requests.config.TestEmailConnectionRequest;
import agsfjope.backend.application.dtos.requests.config.UpdateAiConfigRequest;
import agsfjope.backend.application.dtos.requests.config.UpdateGradingModeRequest;
import agsfjope.backend.application.dtos.requests.config.UpdatePayosConfigRequest;
import agsfjope.backend.application.dtos.requests.config.UpdateSystemSettingsRequest;
import agsfjope.backend.core.enums.GradingMode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for System Admin configuration APIs.
 *
 * Provides endpoints to manage AI config, PayOS config, system settings,
 * and grading mode configurations.
 */
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','ADMIN')")
public class SystemAdminConfigController {

    private final SystemConfigService systemConfigService;
    private final GradingModeConfigService gradingModeConfigService;

    /**
     * Lấy cấu hình AI hiện tại.
     * <p>
     * Cách gọi đúng:
     * <ul>
     *   <li>Method: GET</li>
     *   <li>URL: /api/admin/config/ai</li>
     *   <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     *   <li>Không truyền request body.</li>
     * </ul>
     * Điều kiện phân quyền: tài khoản có quyền SYSTEM_ADMIN hoặc ROLE_SYSTEM_ADMIN.
     *
     * @return dữ liệu cấu hình AI (provider, model, apiKeyMasked, language)
     */
    @GetMapping("/ai")
    public ResponseEntity<Map<String, Object>> getAiConfig() {
        return ResponseEntity.ok(buildSuccessResponse(
                "Lấy cấu hình AI thành công",
                systemConfigService.getAiConfig()
        ));
    }

    /**
     * Cập nhật cấu hình AI.
     * <p>
     * Cách nhập dữ liệu để chạy đúng:
     * <ul>
     *   <li>Method: PUT</li>
     *   <li>URL: /api/admin/config/ai</li>
     *   <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     *   <li>Header bắt buộc: Content-Type: application/json</li>
     *   <li>Body JSON:</li>
     * </ul>
     * <pre>
     * {
     *   "provider": "openai",
     *   "model": "gpt-4o-mini",
     *   "apiKey": "sk-...",
     *   "language": "vi"
     * }
     * </pre>
     * Quy tắc dữ liệu:
     * <ul>
     *   <li>`provider`: bắt buộc, không rỗng.</li>
     *   <li>`model`: bắt buộc, không rỗng.</li>
     *   <li>`apiKey`: không bắt buộc (nếu bỏ trống thì giữ API key cũ).</li>
     *   <li>`language`: chỉ nhận `vi` hoặc `en`.</li>
     * </ul>
     *
     * @param request dữ liệu cấu hình AI mới
     * @param authentication người dùng đã xác thực
     * @return phản hồi thành công
     */
    @PutMapping("/ai")
    public ResponseEntity<Map<String, Object>> updateAiConfig(
            @Valid @RequestBody UpdateAiConfigRequest request,
            Authentication authentication
    ) {
        systemConfigService.updateAiConfig(request, authentication.getName());
        return ResponseEntity.ok(buildSuccessResponse("MSG-73: Cập nhật cấu hình AI thành công", null));
    }

    /**
     * Kiểm tra kết nối thật tới AI provider.
     * <p>
     * Cách nhập dữ liệu để chạy đúng:
     * <ul>
     *   <li>Method: POST</li>
     *   <li>URL: /api/admin/config/ai/test-connection</li>
     *   <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     *   <li>Header bắt buộc: Content-Type: application/json</li>
     * </ul>
     * <pre>
     * {
     *   "provider": "openai",
     *   "model": "gpt-4o-mini",
     *   "apiKey": "sk-..."
     * }
     * </pre>
     * Quy tắc dữ liệu:
     * <ul>
     *   <li>`provider`, `model`, `apiKey` đều bắt buộc và không được để trống.</li>
     *   <li>`provider` có thể là tên nhà cung cấp đã hỗ trợ (openai/gemini/anthropic/...) hoặc URL endpoint theo chuẩn OpenAI-compatible.</li>
     * </ul>
     *
     * @param request dữ liệu test kết nối
     * @return kết quả test gồm `isConnected`, `latencyMs`, `errorMessage`, `testedAt`
     */
    @PostMapping("/ai/test-connection")
    public ResponseEntity<Map<String, Object>> testAiConnection(
            @Valid @RequestBody TestAiConnectionRequest request
    ) {
        var result = systemConfigService.testAiConnection(request);
        String message = Boolean.TRUE.equals(result.getIsConnected())
                ? "MSG-74: Kết nối AI thành công"
                : "MSG-75: Kết nối AI thất bại";
        return ResponseEntity.ok(buildSuccessResponse(message, result));
    }

    /**
     * Lấy cấu hình PayOS hiện tại.
     * <p>
     * Cách gọi đúng:
     * <ul>
     *   <li>Method: GET</li>
     *   <li>URL: /api/admin/config/payos</li>
     *   <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     *   <li>Không truyền request body.</li>
     * </ul>
     *
     * @return dữ liệu cấu hình PayOS đã mask thông tin nhạy cảm
     */
    @GetMapping("/payos")
    public ResponseEntity<Map<String, Object>> getPayosConfig() {
        return ResponseEntity.ok(buildSuccessResponse(
                "Lấy cấu hình PayOS thành công",
                systemConfigService.getPayosConfig()
        ));
    }

    /**
     * Cập nhật cấu hình PayOS.
     * <p>
     * Cách nhập dữ liệu để chạy đúng:
     * <ul>
     *   <li>Method: PUT</li>
     *   <li>URL: /api/admin/config/payos</li>
     *   <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     *   <li>Header bắt buộc: Content-Type: application/json</li>
     * </ul>
     * <pre>
     * {
     *   "clientId": "your-client-id",
     *   "apiKey": "your-api-key",
     *   "checksumKey": "your-checksum-key",
     *   "appealFee": 50000,
     *   "paymentTimeoutMin": 15
     * }
     * </pre>
     * Quy tắc dữ liệu:
     * <ul>
     *   <li>`clientId`, `apiKey`, `checksumKey`: bắt buộc, không rỗng.</li>
     *   <li>`appealFee`: bắt buộc, lớn hơn hoặc bằng 0.</li>
     *   <li>`paymentTimeoutMin`: bắt buộc, lớn hơn hoặc bằng 1.</li>
     * </ul>
     *
     * @param request dữ liệu PayOS mới
     * @param authentication người dùng đã xác thực
     * @return phản hồi thành công
     */
    @PutMapping("/payos")
    public ResponseEntity<Map<String, Object>> updatePayosConfig(
            @Valid @RequestBody UpdatePayosConfigRequest request,
            Authentication authentication
    ) {
        systemConfigService.updatePayosConfig(request, authentication.getName());
        return ResponseEntity.ok(buildSuccessResponse("MSG-76: Cập nhật cấu hình PayOS thành công", null));
    }

    /**
     * Lấy System Settings hiện tại.
     * <p>
     * Cách gọi đúng:
     * <ul>
     *   <li>Method: GET</li>
     *   <li>URL: /api/admin/config/system</li>
     *   <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     *   <li>Không truyền request body.</li>
     * </ul>
     *
     * @return dữ liệu cấu hình hệ thống
     */
    @GetMapping("/system")
    public ResponseEntity<Map<String, Object>> getSystemSettings() {
        return ResponseEntity.ok(buildSuccessResponse(
                "Lấy cấu hình hệ thống thành công",
                systemConfigService.getSystemSettings()
        ));
    }

    /**
     * Cập nhật System Settings.
     * <p>
     * Cách nhập dữ liệu để chạy đúng:
     * <ul>
     *   <li>Method: PUT</li>
     *   <li>URL: /api/admin/config/system</li>
     *   <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     *   <li>Header bắt buộc: Content-Type: application/json</li>
     * </ul>
     * <pre>
     * {
     *   "maxUploadSizeMb": 50,
     *   "maxExamPaperMb": 100,
     *   "smtpHost": "smtp.gmail.com",
     *   "smtpPort": 587,
     *   "smtpUsername": "system@example.com",
     *   "smtpPassword": "app-password",
     *   "smtpFromEmail": "system@example.com",
     *   "defaultGradingMode": "MODE_1"
     * }
     * </pre>
     * Quy tắc dữ liệu:
     * <ul>
     *   <li>`maxUploadSizeMb`, `maxExamPaperMb`: bắt buộc, &gt;= 1.</li>
     *   <li>`smtpHost`, `smtpUsername`, `smtpPassword`: bắt buộc, không rỗng.</li>
     *   <li>`smtpPort`: bắt buộc, trong khoảng 1..65535.</li>
     *   <li>`smtpFromEmail`: bắt buộc, đúng định dạng email.</li>
     *   <li>`defaultGradingMode`: bắt buộc, một trong MODE_1, MODE_2, MODE_3, MODE_4.</li>
     * </ul>
     *
     * @param request dữ liệu system settings mới
     * @param authentication người dùng đã xác thực
     * @return phản hồi thành công
     */
    @PutMapping("/system")
    public ResponseEntity<Map<String, Object>> updateSystemSettings(
            @Valid @RequestBody UpdateSystemSettingsRequest request,
            Authentication authentication
    ) {
        systemConfigService.updateSystemSettings(request, authentication.getName());
        return ResponseEntity.ok(buildSuccessResponse("MSG-83: Cập nhật System Settings thành công", null));
    }

        /**
         * Kiểm tra kết nối SMTP Email runtime.
         * <p>
         * Cách nhập dữ liệu để chạy đúng:
         * <ul>
         *   <li>Method: POST</li>
         *   <li>URL: /api/admin/config/system/test-connection</li>
         *   <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
         *   <li>Header bắt buộc: Content-Type: application/json</li>
         * </ul>
         * <pre>
         * {
         *   "smtpHost": "smtp.gmail.com",
         *   "smtpPort": 587,
         *   "smtpUsername": "system@example.com",
         *   "smtpPassword": "app-password",
         *   "smtpFromEmail": "system@example.com",
         *   "testToEmail": "receiver@example.com"
         * }
         * </pre>
         * Quy tắc dữ liệu:
         * <ul>
         *   <li>`smtpHost`, `smtpUsername`, `smtpPassword`: bắt buộc, không rỗng.</li>
         *   <li>`smtpPort`: bắt buộc, &gt;= 1.</li>
         *   <li>`smtpFromEmail`, `testToEmail`: bắt buộc, đúng định dạng email.</li>
         * </ul>
        * Endpoint sẽ thực hiện kết nối SMTP và gửi email test thật đến `testToEmail`.
         *
         * @param request dữ liệu test SMTP
         * @return kết quả test kết nối SMTP
         */
        @PostMapping("/system/test-connection")
        public ResponseEntity<Map<String, Object>> testEmailConnection(
            @Valid @RequestBody TestEmailConnectionRequest request
        ) {
        var result = systemConfigService.testEmailConnection(request);
        String message = Boolean.TRUE.equals(result.getIsConnected())
            ? "Kết nối máy chủ Email (SMTP) thành công"
            : "Kết nối máy chủ Email (SMTP) thất bại";
        return ResponseEntity.ok(buildSuccessResponse(message, result));
        }

    /**
     * Lấy toàn bộ cấu hình grading mode và mode mặc định.
     * <p>
     * Cách gọi đúng:
     * <ul>
     *   <li>Method: GET</li>
     *   <li>URL: /api/admin/config/grading-modes</li>
     *   <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     *   <li>Không truyền request body.</li>
     * </ul>
     *
     * @return danh sách grading mode
     */
    @GetMapping("/grading-modes")
    public ResponseEntity<Map<String, Object>> getAllGradingModes() {
        return ResponseEntity.ok(buildSuccessResponse(
                "Lấy danh sách Grading Modes thành công",
                gradingModeConfigService.getAllGradingModes()
        ));
    }

    /**
     * Lấy chi tiết một grading mode theo path variable.
     * <p>
     * Cách gọi đúng:
     * <ul>
     *   <li>Method: GET</li>
     *   <li>URL mẫu: /api/admin/config/grading-modes/MODE_1</li>
     *   <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     *   <li>Không truyền request body.</li>
     * </ul>
     * Giá trị hợp lệ của `mode`: MODE_1, MODE_2, MODE_3, MODE_4.
     *
     * @param mode grading mode trong URL
     * @return chi tiết grading mode
     */
    @GetMapping("/grading-modes/{mode}")
    public ResponseEntity<Map<String, Object>> getGradingModeDetail(@PathVariable GradingMode mode) {
        return ResponseEntity.ok(buildSuccessResponse(
                "Lấy chi tiết Grading Mode thành công",
                gradingModeConfigService.getGradingModeDetail(mode)
        ));
    }

    /**
     * Cập nhật một grading mode theo `mode` trên URL.
     * <p>
     * Cách nhập dữ liệu để chạy đúng:
     * <ul>
     *   <li>Method: PUT</li>
     *   <li>URL mẫu: /api/admin/config/grading-modes/MODE_2</li>
     *   <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     *   <li>Header bắt buộc: Content-Type: application/json</li>
     * </ul>
     * <pre>
     * {
     *   "displayName": "Hybrid Mode",
     *   "testCaseWeight": 70,
     *   "oopWeight": 30,
     *   "oopCommentOnly": false,
     *   "failIfZeroTestCase": true,
     *   "failIfOopViolated": false,
     *   "isActive": true,
     *   "description": "Scoring by testcase + oop"
     * }
     * </pre>
     * Quy tắc dữ liệu:
     * <ul>
     *   <li>`displayName`: bắt buộc, không rỗng.</li>
     *   <li>`testCaseWeight`, `oopWeight`: bắt buộc, trong khoảng 0..100.</li>
     *   <li>Tổng `testCaseWeight + oopWeight` phải bằng 100.</li>
     *   <li>Các cờ boolean (`oopCommentOnly`, `failIfZeroTestCase`, `failIfOopViolated`, `isActive`) đều bắt buộc.</li>
     *   <li>`mode` hợp lệ: MODE_1, MODE_2, MODE_3, MODE_4.</li>
     * </ul>
     *
     * @param mode grading mode trong URL
     * @param request dữ liệu cập nhật mode
     * @return phản hồi thành công
     */
    @PutMapping("/grading-modes/{mode}")
    public ResponseEntity<Map<String, Object>> updateGradingMode(
            @PathVariable GradingMode mode,
            @Valid @RequestBody UpdateGradingModeRequest request
    ) {
        gradingModeConfigService.updateGradingMode(mode, request);
        return ResponseEntity.ok(buildSuccessResponse("MSG-79: Cập nhật Grading Mode thành công", null));
    }

    /**
     * Đặt một grading mode làm mode mặc định của hệ thống.
     * <p>
     * Cách gọi đúng:
     * <ul>
     *   <li>Method: PUT</li>
     *   <li>URL mẫu: /api/admin/config/grading-modes/MODE_3/set-default</li>
     *   <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     *   <li>Không truyền request body.</li>
     * </ul>
     * Giá trị hợp lệ của `mode`: MODE_1, MODE_2, MODE_3, MODE_4.
     *
     * @param mode mode sẽ được đặt mặc định
     * @param authentication người dùng đã xác thực
     * @return phản hồi thành công
     */
    @PutMapping("/grading-modes/{mode}/set-default")
    public ResponseEntity<Map<String, Object>> setDefaultGradingMode(
            @PathVariable GradingMode mode,
            Authentication authentication
    ) {
        gradingModeConfigService.setDefaultGradingMode(mode, authentication.getName());
        return ResponseEntity.ok(buildSuccessResponse("MSG-84: Cập nhật default Grading Mode thành công", null));
    }

    /**
     * Build standardized API success response payload.
     * Format: { success, message, data, errors }
     *
     * @param message success message
     * @param data    payload (nullable)
     * @return response map
     */
    private Map<String, Object> buildSuccessResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        response.put("errors", null);
        return response;
    }
}
