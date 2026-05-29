package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.configservices.GradingModeConfigService;
import agsfjope.backend.application.configservices.SystemConfigService;
import agsfjope.backend.application.dtos.requests.config.CreateGradingModeRequest;
import agsfjope.backend.application.dtos.requests.config.TestAiConnectionRequest;
import agsfjope.backend.application.dtos.requests.config.TestEmailConnectionRequest;
import agsfjope.backend.application.dtos.requests.config.UpdateAiConfigRequest;
import agsfjope.backend.application.dtos.requests.config.UpdateEmailConfigRequest;
import agsfjope.backend.application.dtos.requests.config.UpdateGradingModeRequest;
import agsfjope.backend.application.dtos.requests.config.UpdatePassThresholdRequest;
import agsfjope.backend.application.dtos.requests.config.UpdatePayosConfigRequest;
import agsfjope.backend.application.dtos.requests.config.UpdateSystemSettingsRequest;
import agsfjope.backend.application.dtos.responses.config.SystemSettingsResponse;
import agsfjope.backend.core.enums.GradingMode;
import agsfjope.backend.infrastructure.storage.MinioPathMigrationService;
import agsfjope.backend.application.paymentservices.HandlePaymentService;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
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

    private final SystemConfigService          systemConfigService;
    private final GradingModeConfigService      gradingModeConfigService;
    private final MinioPathMigrationService     minioMigrationService;
    private final HandlePaymentService          handlePaymentService;

    /**
     * Lấy cấu hình AI hiện tại.
     * <p>
     * Cách gọi đúng:
     * <ul>
     * <li>Method: GET</li>
     * <li>URL: /api/admin/config/ai</li>
     * <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     * <li>Không truyền request body.</li>
     * </ul>
     * Điều kiện phân quyền: tài khoản có quyền SYSTEM_ADMIN hoặc ROLE_SYSTEM_ADMIN.
     *
     * @return dữ liệu cấu hình AI (provider, model, apiKeyMasked, language)
     */
    @GetMapping("/ai")
    public ResponseEntity<Map<String, Object>> getAiConfig() {
        return ResponseEntity.ok(buildSuccessResponse(
                "Lấy cấu hình AI thành công",
                systemConfigService.getAiConfig()));
    }

    /**
     * Cập nhật cấu hình AI.
     * <p>
     * Cách nhập dữ liệu để chạy đúng:
     * <ul>
     * <li>Method: PUT</li>
     * <li>URL: /api/admin/config/ai</li>
     * <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     * <li>Header bắt buộc: Content-Type: application/json</li>
     * <li>Body JSON:</li>
     * </ul>
     * 
     * <pre>
     * {
     *   "provider": "openai",
     *   "model": "gpt-4o-mini",
     *   "apiKey": "sk-...",
     *   "language": "vi"
     * }
     * </pre>
     * 
     * Quy tắc dữ liệu:
     * <ul>
     * <li>`provider`: bắt buộc, không rỗng.</li>
     * <li>`model`: bắt buộc, không rỗng.</li>
     * <li>`apiKey`: không bắt buộc (nếu bỏ trống thì giữ API key cũ).</li>
     * <li>`language`: chỉ nhận `vi` hoặc `en`.</li>
     * </ul>
     *
     * @param request        dữ liệu cấu hình AI mới
     * @param authentication người dùng đã xác thực
     * @return phản hồi thành công
     */
    @PutMapping("/ai")
    public ResponseEntity<Map<String, Object>> updateAiConfig(
            @Valid @RequestBody UpdateAiConfigRequest request,
            Authentication authentication) {
        systemConfigService.updateAiConfig(request, authentication.getName());
        return ResponseEntity.ok(buildSuccessResponse("MSG-73: Cập nhật cấu hình AI thành công", null));
    }

    /**
     * Kiểm tra kết nối thật tới AI provider.
     * <p>
     * Cách nhập dữ liệu để chạy đúng:
     * <ul>
     * <li>Method: POST</li>
     * <li>URL: /api/admin/config/ai/test-connection</li>
     * <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     * <li>Header bắt buộc: Content-Type: application/json</li>
     * </ul>
     * 
     * <pre>
     * {
     *   "provider": "openai",
     *   "model": "gpt-4o-mini",
     *   "apiKey": "sk-..."
     * }
     * </pre>
     * 
     * Quy tắc dữ liệu:
     * <ul>
     * <li>`provider`, `model`, `apiKey` đều bắt buộc và không được để trống.</li>
     * <li>`provider` có thể là tên nhà cung cấp đã hỗ trợ
     * (openai/gemini/anthropic/...) hoặc URL endpoint theo chuẩn
     * OpenAI-compatible.</li>
     * </ul>
     *
     * @param request dữ liệu test kết nối
     * @return kết quả test gồm `isConnected`, `latencyMs`, `errorMessage`,
     *         `testedAt`
     */
    @PostMapping("/ai/test-connection")
    public ResponseEntity<Map<String, Object>> testAiConnection(
            @Valid @RequestBody TestAiConnectionRequest request) {
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
     * <li>Method: GET</li>
     * <li>URL: /api/admin/config/payos</li>
     * <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     * <li>Không truyền request body.</li>
     * </ul>
     *
     * @return dữ liệu cấu hình PayOS đã mask thông tin nhạy cảm
     */
    @GetMapping("/payos")
    public ResponseEntity<Map<String, Object>> getPayosConfig() {
        return ResponseEntity.ok(buildSuccessResponse(
                "Lấy cấu hình PayOS thành công",
                systemConfigService.getPayosConfig()));
    }

    /**
     * Cập nhật cấu hình PayOS.
     * <p>
     * Cách nhập dữ liệu để chạy đúng:
     * <ul>
     * <li>Method: PUT</li>
     * <li>URL: /api/admin/config/payos</li>
     * <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     * <li>Header bắt buộc: Content-Type: application/json</li>
     * </ul>
     * 
     * <pre>
     * {
     *   "clientId": "your-client-id",
     *   "apiKey": "your-api-key",
     *   "checksumKey": "your-checksum-key",
     *   "appealFee": 200000,
     *   "paymentTimeoutMin": 15
     * }
     * </pre>
     * 
     * Quy tắc dữ liệu:
     * <ul>
     * <li>`clientId`, `apiKey`, `checksumKey`: bắt buộc, không rỗng.</li>
     * <li>`appealFee`: bắt buộc, lớn hơn hoặc bằng 0.</li>
     * <li>`paymentTimeoutMin`: bắt buộc, lớn hơn hoặc bằng 1.</li>
     * </ul>
     *
     * @param request        dữ liệu PayOS mới
     * @param authentication người dùng đã xác thực
     * @return phản hồi thành công
     */
    @PutMapping("/payos")
    public ResponseEntity<Map<String, Object>> updatePayosConfig(
            @Valid @RequestBody UpdatePayosConfigRequest request,
            Authentication authentication) {
        systemConfigService.updatePayosConfig(request, authentication.getName());
        return ResponseEntity.ok(buildSuccessResponse("MSG-76: Cập nhật cấu hình PayOS thành công", null));
    }

    /**
     * Lấy System Settings hiện tại.
     * <p>
     * Cách gọi đúng:
     * <ul>
     * <li>Method: GET</li>
     * <li>URL: /api/admin/config/system</li>
     * <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     * <li>Không truyền request body.</li>
     * </ul>
     *
     * @return dữ liệu cấu hình hệ thống
     */
    @GetMapping("/system")
    public ResponseEntity<Map<String, Object>> getSystemSettings() {
        return ResponseEntity.ok(buildSuccessResponse(
                "Lấy cấu hình hệ thống thành công",
                systemConfigService.getSystemSettings()));
    }

    /**
     * Cập nhật System Settings (giới hạn upload và grading mode mặc định).
     * <p>
     * Cách nhập dữ liệu để chạy đúng:
     * <ul>
     * <li>Method: PUT</li>
     * <li>URL: /api/admin/config/system</li>
     * <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     * <li>Header bắt buộc: Content-Type: application/json</li>
     * </ul>
     * 
     * <pre>
     * {
     *   "maxUploadSizeMb": 50,
     *   "maxExamPaperMb": 100,
     *   "defaultGradingMode": "MODE_1"
     * }
     * </pre>
     * 
     * @param request        dữ liệu system settings mới
     * @param authentication người dùng đã xác thực
     * @return phản hồi thành công
     */
    @PutMapping("/system")
    public ResponseEntity<Map<String, Object>> updateSystemSettings(
            @Valid @RequestBody UpdateSystemSettingsRequest request,
            Authentication authentication) {
        systemConfigService.updateSystemSettings(request, authentication.getName());
        return ResponseEntity.ok(buildSuccessResponse("MSG-83: Cập nhật System Settings thành công", null));
    }

    /**
     * Cập nhật ngưỡng điểm đạt (Grading Pass Threshold) cho hệ thống.
     * <p>
     * Cách nhập dữ liệu để chạy đúng:
     * <ul>
     * <li>Method: PUT</li>
     * <li>URL: /api/admin/config/system/pass-threshold</li>
     * <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     * <li>Header bắt buộc: Content-Type: application/json</li>
     * </ul>
     *
     * <pre>
     * {
     *   "passThreshold": 4.0
     * }
     * </pre>
     *
     * Quy tắc dữ liệu:
     * <ul>
     * <li>{@code passThreshold}: bắt buộc, &gt;= 0.</li>
     * <li>Điểm cuối của sinh viên phải GREATER THAN giá trị này mới được PASS.</li>
     * <li>Thay đổi có hiệu lực ngay lập tức — không cần restart server.</li>
     * </ul>
     *
     * @param request        dữ liệu ngưỡng điểm mới
     * @param authentication người dùng đã xác thực
     * @return phản hồi thành công
     */
    @PutMapping("/system/pass-threshold")
    public ResponseEntity<Map<String, Object>> updatePassThreshold(
            @Valid @RequestBody UpdatePassThresholdRequest request,
            Authentication authentication) {
        systemConfigService.updatePassThreshold(request, authentication.getName());
        return ResponseEntity.ok(buildSuccessResponse(
                "MSG-85: Cập nhật ngưỡng điểm đạt thành công", null));
    }

    /**
     * Kiểm tra kết nối SMTP Email runtime.
     * <p>
     * Cách nhập dữ liệu để chạy đúng:
     * <ul>
     * <li>Method: POST</li>
     * <li>URL: /api/admin/config/system/test-connection</li>
     * <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     * <li>Header bắt buộc: Content-Type: application/json</li>
     * </ul>
     * 
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
     * 
     * Endpoint sẽ thực hiện kết nối SMTP và gửi email test thật đến
     * {@code testToEmail}.
     *
     * @param request dữ liệu test SMTP
     * @return kết quả test kết nối SMTP
     */
    @PostMapping("/system/test-connection")
    public ResponseEntity<Map<String, Object>> testEmailConnection(
            @Valid @RequestBody TestEmailConnectionRequest request) {
        var result = systemConfigService.testEmailConnection(request);
        String message = Boolean.TRUE.equals(result.getIsConnected())
                ? "Kết nối máy chủ Email (SMTP) thành công"
                : "Kết nối máy chủ Email (SMTP) thất bại";
        return ResponseEntity.ok(buildSuccessResponse(message, result));
    }

    /**
     * Cập nhật cấu hình SMTP Email.
     * <p>
     * Cách nhập dữ liệu để chạy đúng:
     * <ul>
     * <li>Method: PUT</li>
     * <li>URL: /api/admin/config/system/email</li>
     * <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     * <li>Header bắt buộc: Content-Type: application/json</li>
     * </ul>
     * 
     * <pre>
     * {
     *   "smtpHost": "smtp.gmail.com",
     *   "smtpPort": 587,
     *   "smtpUsername": "system@example.com",
     *   "smtpPassword": "app-password",
     *   "smtpFromEmail": "system@example.com"
     * }
     * </pre>
     * 
     * Tất cả các trường đều bắt buộc và phải hợp lệ.
     *
     * @param request        dữ liệu SMTP mới
     * @param authentication người dùng đã xác thực
     * @return phản hồi thành công
     */
    @PutMapping("/system/email")
    public ResponseEntity<Map<String, Object>> updateEmailConfig(
            @Valid @RequestBody UpdateEmailConfigRequest request,
            Authentication authentication) {
        systemConfigService.updateEmailConfig(request, authentication.getName());
        return ResponseEntity.ok(buildSuccessResponse("Cập nhật cấu hình Email (SMTP) thành công", null));
    }

    /**
     * Lấy toàn bộ cấu hình grading mode và mode mặc định.
     * <p>
     * Cách gọi đúng:
     * <ul>
     * <li>Method: GET</li>
     * <li>URL: /api/admin/config/grading-modes</li>
     * <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     * <li>Không truyền request body.</li>
     * </ul>
     *
     * @return danh sách grading mode
     */
    @GetMapping("/grading-modes")
    public ResponseEntity<Map<String, Object>> getAllGradingModes() {
        return ResponseEntity.ok(buildSuccessResponse(
                "Lấy danh sách Grading Modes thành công",
                gradingModeConfigService.getAllGradingModes()));
    }

    /**
     * Lấy chi tiết một grading mode theo path variable.
     * <p>
     * Cách gọi đúng:
     * <ul>
     * <li>Method: GET</li>
     * <li>URL mẫu: /api/admin/config/grading-modes/MODE_1</li>
     * <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     * <li>Không truyền request body.</li>
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
                gradingModeConfigService.getGradingModeDetail(mode)));
    }

    /**
     * Tạo mới một grading mode configuration.
     * <p>
     * Cách gọi đúng:
     * <ul>
     * <li>Method: POST</li>
     * <li>URL: /api/admin/config/grading-modes</li>
     * <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     * <li>Header bắt buộc: Content-Type: application/json</li>
     * </ul>
     *
     * @param request dữ liệu tạo mới mode
     * @return phản hồi thành công
     */
    @PostMapping("/grading-modes")
    public ResponseEntity<Map<String, Object>> createGradingMode(
            @Valid @RequestBody CreateGradingModeRequest request) {
        gradingModeConfigService.createGradingMode(request);
        return ResponseEntity.ok(buildSuccessResponse("Tạo Grading Mode thành công", null));
    }

    /**
     * Cập nhật một grading mode theo `mode` trên URL.
     * <p>
     * Cách nhập dữ liệu để chạy đúng:
     * <ul>
     * <li>Method: PUT</li>
     * <li>URL mẫu: /api/admin/config/grading-modes/MODE_2</li>
     * <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     * <li>Header bắt buộc: Content-Type: application/json</li>
     * </ul>
     * 
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
     * 
     * Quy tắc dữ liệu:
     * <ul>
     * <li>`displayName`: bắt buộc, không rỗng.</li>
     * <li>`testCaseWeight`, `oopWeight`: bắt buộc, trong khoảng 0..100.</li>
     * <li>Tổng `testCaseWeight + oopWeight` phải bằng 100.</li>
     * <li>Các cờ boolean (`oopCommentOnly`, `failIfZeroTestCase`,
     * `failIfOopViolated`, `isActive`) đều bắt buộc.</li>
     * <li>`mode` hợp lệ: MODE_1, MODE_2, MODE_3, MODE_4.</li>
     * </ul>
     *
     * @param mode    grading mode trong URL
     * @param request dữ liệu cập nhật mode
     * @return phản hồi thành công
     */
    @PutMapping("/grading-modes/{mode}")
    public ResponseEntity<Map<String, Object>> updateGradingMode(
            @PathVariable GradingMode mode,
            @Valid @RequestBody UpdateGradingModeRequest request) {
        gradingModeConfigService.updateGradingMode(mode, request);
        return ResponseEntity.ok(buildSuccessResponse("MSG-79: Cập nhật Grading Mode thành công", null));
    }

    /**
     * Đặt một grading mode làm mode mặc định của hệ thống.
     * <p>
     * Cách gọi đúng:
     * <ul>
     * <li>Method: PUT</li>
     * <li>URL mẫu: /api/admin/config/grading-modes/MODE_3/set-default</li>
     * <li>Header bắt buộc: Authorization: Bearer &lt;jwt_token&gt;</li>
     * <li>Không truyền request body.</li>
     * </ul>
     * Giá trị hợp lệ của `mode`: MODE_1, MODE_2, MODE_3, MODE_4.
     *
     * @param mode           mode sẽ được đặt mặc định
     * @param authentication người dùng đã xác thực
     * @return phản hồi thành công
     */
    @PutMapping("/grading-modes/{mode}/set-default")
    public ResponseEntity<Map<String, Object>> setDefaultGradingMode(
            @PathVariable GradingMode mode,
            Authentication authentication) {
        gradingModeConfigService.setDefaultGradingMode(mode, authentication.getName());
        return ResponseEntity.ok(buildSuccessResponse("MSG-84: Cập nhật default Grading Mode thành công", null));
    }

    /**
     * Lấy cấu hình hệ thống dành cho sinh viên (public config).
     * <p>
     * Chỉ trả về các thông tin cần thiết, không lộ dữ liệu nhạy cảm.
     * <ul>
     * <li>Method: GET</li>
     * <li>URL: /api/config/public — dùng trên frontend student</li>
     * </ul>
     *
     * @return { maxUploadSizeMb }
     */
    @GetMapping("/public")
    @PreAuthorize("hasAnyAuthority('STUDENT','ROLE_STUDENT','EXAM_STAFF','ROLE_EXAM_STAFF','SYSTEM_ADMIN','ROLE_SYSTEM_ADMIN','ADMIN','ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getPublicConfig() {
        SystemSettingsResponse settings = systemConfigService.getSystemSettings();
        Map<String, Object> publicData = new HashMap<>();
        publicData.put("maxUploadSizeMb", settings.getMaxUploadSizeMb());
        return ResponseEntity.ok(buildSuccessResponse("Lấy cấu hình công khai thành công", publicData));
    }

    /**
     * Migrate MinIO object paths từ format cũ (UUID-based) sang format mới (human-readable).
     * <p>
     * Gọi cách đúng:
     * <ul>
     *   <li>Method: POST</li>
     *   <li>URL: /api/admin/config/minio-migration?dryRun=true (preview) hoặc ?dryRun=false (thực hiện)</li>
     *   <li>Header: Authorization: Bearer &lt;jwt_token&gt;</li>
     * </ul>
     *
     * <p>Format cũ → mới:</p>
     * <pre>
     * ExamPaper: exam-papers/exams/{id}/blocks/{id}/{file}  →  exam-papers/{Semester}-{Year}/{Block}/{file}
     * Submission: submissions/exams/{id}/blocks/{id}/students/{id}/{file}  →  submissions/{Semester}-{Year}/{Block}/{Name} - {MSSV}/{file}
     * </pre>
     *
     * @param dryRun nếu true: chỉ log, không thực sự sửa
     * @return báo cáo số lượng migrated / skipped / failed
     */
    @PostMapping("/minio-migration")
    public ResponseEntity<Map<String, Object>> migrateMinioPath(
            @RequestParam(defaultValue = "false") boolean dryRun) {
        MinioPathMigrationService.MigrationReport report = minioMigrationService.migrateAll(dryRun);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("dryRun",              dryRun);
        data.put("examPapersMigrated",  report.examPapersMigrated());
        data.put("examPapersSkipped",   report.examPapersSkipped());
        data.put("examPapersFailed",    report.examPapersFailed());
        data.put("submissionsMigrated", report.submissionsMigrated());
        data.put("submissionsSkipped",  report.submissionsSkipped());
        data.put("submissionsFailed",   report.submissionsFailed());
        data.put("errors",              report.errors());
        String msg = dryRun
                ? "Dry-run MinIO migration hoàn tất — không có thay đổi thực sự"
                : "MinIO migration hoàn tất";
        return ResponseEntity.ok(buildSuccessResponse(msg, data));
    }

    /**
     * Sửa object trên MinIO từ path cũ sang path mới,
     * khi DB đã có path mới nhưng MinIO vẫn còn file ở path cũ (UUID-based).
     * <p>
     * Gọi cách đúng:
     * <ul>
     *   <li>Method: POST</li>
     *   <li>URL: /api/admin/config/minio-fix?dryRun=true (preview) hoặc ?dryRun=false</li>
     *   <li>Header: Authorization: Bearer &lt;jwt_token&gt;</li>
     * </ul>
     *
     * @param dryRun nếu true: chỉ log, không thực sự copy/xóa MinIO object
     */
    @PostMapping("/minio-fix")
    public ResponseEntity<Map<String, Object>> fixMinioObjects(
            @RequestParam(defaultValue = "false") boolean dryRun) {
        MinioPathMigrationService.MigrationReport report = minioMigrationService.fixMinioObjects(dryRun);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("dryRun",              dryRun);
        data.put("examPapersMigrated",  report.examPapersMigrated());
        data.put("examPapersSkipped",   report.examPapersSkipped());
        data.put("examPapersFailed",    report.examPapersFailed());
        data.put("submissionsMigrated", report.submissionsMigrated());
        data.put("submissionsSkipped",  report.submissionsSkipped());
        data.put("submissionsFailed",   report.submissionsFailed());
        data.put("errors",              report.errors());
        String msg = dryRun
                ? "Dry-run MinIO fix hoàn tất — không có thay đổi thực sự"
                : "MinIO fix hoàn tất — đã dời object sang path mới";
        return ResponseEntity.ok(buildSuccessResponse(msg, data));
    }

    /**
     * Sửa object trên MinIO và DB bằng cách cắt bỏ phần duplicate folder 
     * (thừa `exam-papers/` và `submissions/` khi bucket name đã trùng).
     * <p>
     * Gọi cách đúng:
     * <ul>
     *   <li>Method: POST</li>
     *   <li>URL: /api/admin/config/minio-fix-duplicate-prefixes?dryRun=true (preview) hoặc ?dryRun=false</li>
     *   <li>Header: Authorization: Bearer &lt;jwt_token&gt;</li>
     * </ul>
     *
     * @param dryRun nếu true: chỉ log, không thực sự copy/xóa MinIO object
     */
    @PostMapping("/minio-fix-duplicate-prefixes")
    public ResponseEntity<Map<String, Object>> fixDuplicatePrefixes(
            @RequestParam(defaultValue = "false") boolean dryRun) {
        MinioPathMigrationService.MigrationReport report = minioMigrationService.fixDuplicatePrefixes(dryRun);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("dryRun",              dryRun);
        data.put("examPapersMigrated",  report.examPapersMigrated());
        data.put("examPapersSkipped",   report.examPapersSkipped());
        data.put("examPapersFailed",    report.examPapersFailed());
        data.put("submissionsMigrated", report.submissionsMigrated());
        data.put("submissionsSkipped",  report.submissionsSkipped());
        data.put("submissionsFailed",   report.submissionsFailed());
        data.put("errors",              report.errors());
        String msg = dryRun
                ? "Dry-run MinIO fix duplicate prefixes hoàn tất — không có thay đổi thực sự"
                : "MinIO fix duplicate prefixes hoàn tất — đã dời object và cập nhật DB";
        return ResponseEntity.ok(buildSuccessResponse(msg, data));
    }

    /**
     * Backfill jarFilePath và sourceCodePath cho các Answer records còn NULL.
     * Re-download từng submission zip từ MinIO, parse lại, và cập nhật DB.
     * <p>
     * Gọi cách đúng:
     * <ul>
     *   <li>Method: POST</li>
     *   <li>URL: /api/admin/config/backfill-answer-paths?dryRun=true (preview) hoặc ?dryRun=false</li>
     *   <li>Header: Authorization: Bearer &lt;jwt_token&gt;</li>
     * </ul>
     *
     * @param dryRun nếu true: chỉ log, không ghi vào DB
     */
    @PostMapping("/backfill-answer-paths")
    public ResponseEntity<Map<String, Object>> backfillAnswerPaths(
            @RequestParam(defaultValue = "false") boolean dryRun) {
        MinioPathMigrationService.BackfillReport report = minioMigrationService.backfillAnswerPaths(dryRun);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("dryRun",             dryRun);
        data.put("submissionsScanned", report.submissionsScanned());
        data.put("answersUpdated",     report.answersUpdated());
        data.put("answersSkipped",     report.answersSkipped());
        data.put("failed",             report.failed());
        data.put("errors",             report.errors());
        String msg = dryRun
                ? "Dry-run backfill hoàn tất — không có thay đổi thực sự"
                : "Backfill hoàn tất — đã cập nhật jarFilePath và sourceCodePath cho các Answer";
        return ResponseEntity.ok(buildSuccessResponse(msg, data));
    }

    /**
     * Lấy toàn bộ danh sách giao dịch trên hệ thống cho Admin với bộ lọc nâng cao và phân trang.
     * GET /api/admin/config/payments?page=0&size=15&from=...&to=...&search=...
     */
    @GetMapping("/payments")
    public ResponseEntity<Map<String, Object>> getAllPayments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        var pageable = org.springframework.data.domain.PageRequest.of(page, Math.min(size, 100));
        var pageResult = handlePaymentService.getAllPaymentsForAdminPaged(from, to, search, pageable);

        Map<String, Object> pagination = new java.util.LinkedHashMap<>();
        pagination.put("page", pageResult.getNumber());
        pagination.put("size", pageResult.getSize());
        pagination.put("totalElements", pageResult.getTotalElements());
        pagination.put("totalPages", pageResult.getTotalPages());
        pagination.put("first", pageResult.isFirst());
        pagination.put("last", pageResult.isLast());

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("content", pageResult.getContent());
        data.put("pagination", pagination);

        return ResponseEntity.ok(buildSuccessResponse("Lấy danh sách giao dịch thành công", data));
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
