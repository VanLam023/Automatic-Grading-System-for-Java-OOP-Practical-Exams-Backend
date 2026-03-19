package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.requests.user.CreateUserRequest;
import agsfjope.backend.application.dtos.responses.user.CreateUserResponse;
import agsfjope.backend.application.dtos.responses.user.ImportStudentResponse;
import agsfjope.backend.application.usermanagementservices.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for Admin user management operations.
 * All endpoints require the SYSTEM_ADMIN role and use the standard
 * {@code { success, message, data, errors }} response format.
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserManagementService userManagementService;

    /**
     * Bulk-imports student accounts from an uploaded Excel (.xlsx) file.
     *
     * <p>
     * The system automatically:
     * <ol>
     * <li>Parses the Excel file (columns: Email, FullName, MSSV)</li>
     * <li>Derives username from email (everything before '@')</li>
     * <li>Sets default password: {@code Abc@123} (BCrypt-hashed)</li>
     * <li>Creates accounts with {@code isActive = false} — student must change
     * password to activate</li>
     * <li>Sends credential email to each student asynchronously</li>
     * </ol>
     * </p>
     *
     * <p>
     * Rows with duplicate email, username, or MSSV are skipped and listed in
     * {@code skippedDetails}.
     * </p>
     *
     * @param file the .xlsx file containing student data (columns: Email, FullName,
     *             MSSV)
     * @return summary of the import: successCount, skippedCount, and detailed
     *         skipped row list
     */
    @PostMapping(value = "/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> importStudentsFromExcel(
            @RequestParam("file") MultipartFile file) {

        // Validate that the uploaded file is not empty before delegating to service
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(buildErrorResponse("File không được để trống"));
        }

        // Validate file extension — only .xlsx is supported (Apache POI XSSFWorkbook)
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".xlsx")) {
            return ResponseEntity.badRequest()
                    .body(buildErrorResponse("Chỉ chấp nhận file định dạng .xlsx"));
        }

        // Delegate all parsing/validation/account-creation logic to the service layer
        ImportStudentResponse result = userManagementService.importStudentsFromExcel(file);

        return ResponseEntity.ok(buildSuccessResponse(
                "Import hoàn tất: " + result.getSuccessCount() + " tài khoản được tạo, "
                        + result.getSkippedCount() + " dòng bị bỏ qua",
                result));
    }

    /**
     * POST /api/admin/users/create
     * Tạo thủ công 1 tài khoản với role STUDENT | EXAM_STAFF | LECTURER.
     *
     * <p>
     * Body JSON:
     * 
     * <pre>{@code
     * {
     *   "roleName" : "STUDENT" | "EXAM_STAFF" | "LECTURER",
     *   "email"    : "user@example.com",
     *   "fullName" : "Nguyen Van A",
     *   "mssv"     : "SE170601"  // optional; nếu có và role=STUDENT thì email phải @fpt.edu.vn
     * }
     * }</pre>
     * </p>
     */
    @PostMapping("/create")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        try {
            CreateUserResponse result = userManagementService.createUser(request);
            return ResponseEntity.ok(buildSuccessResponse(
                    "Tài khoản '" + result.getUsername() + "' (" + result.getRoleName()
                            + ") đã được tạo. Email kích hoạt đã gửi.",
                    result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(buildErrorResponse(e.getMessage()));
        }
    }

    /**
     * DELETE /api/admin/users/{userId}
     * Soft-deletes a user: sets deletedAt = now, isActive = false, isLocked = true.
     * Cannot delete accounts with role SYSTEM_ADMIN.
     *
     * @param userId UUID của user cần xoá
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable UUID userId) {
        try {
            userManagementService.deleteUser(userId);
            return ResponseEntity.ok(buildSuccessResponse(
                    "Tài khoản đã được xoá thành công (soft delete).", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(buildErrorResponse(e.getMessage()));
        }
    }

    /**
     * PATCH /api/admin/users/{userId}/activate
     * Admin kích hoạt thủ công một tài khoản chưa active.
     *
     * @param userId UUID của user cần kích hoạt
     */
    @PatchMapping("/{userId}/activate")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> activateUser(@PathVariable UUID userId) {
        try {
            userManagementService.activateUser(userId);
            return ResponseEntity.ok(buildSuccessResponse(
                    "Tài khoản đã được kích hoạt thành công.", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(buildErrorResponse(e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers — matches the standard response format used throughout the project
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the standard success response map.
     * Format: {@code { "success": true, "message": "...", "data": {...}, "errors":
     * null }}
     *
     * @param message human-readable success message
     * @param data    response payload
     * @return standardized map ready to be serialized by Spring
     */
    private Map<String, Object> buildSuccessResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        response.put("errors", null);
        return response;
    }

    /**
     * Builds the standard error response map.
     * Format: {@code { "success": false, "message": "...", "data": null, "errors":
     * "..." }}
     *
     * @param errorMessage human-readable error description
     * @return standardized error map
     */
    private Map<String, Object> buildErrorResponse(String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", errorMessage);
        response.put("data", null);
        response.put("errors", errorMessage);
        return response;
    }
}
