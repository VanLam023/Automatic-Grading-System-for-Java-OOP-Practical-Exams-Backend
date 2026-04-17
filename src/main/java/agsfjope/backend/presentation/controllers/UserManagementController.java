package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.requests.user.CreateUserRequest;
import agsfjope.backend.application.dtos.responses.user.CreateUserResponse;
import agsfjope.backend.application.dtos.responses.user.ImportStudentResponse;
import agsfjope.backend.application.dtos.responses.user.UserDetailResponse;
import agsfjope.backend.application.usermanagementservices.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
     * Tạo thủ công 1 tài khoản với role STUDENT | EXAM_STAFF | LECTURER | SYSTEM_ADMIN.
     *
     * <p>
     * Body JSON:
     * 
     * <pre>{@code
     * {
     *   "roleName" : "STUDENT" | "EXAM_STAFF" | "LECTURER" | "SYSTEM_ADMIN",
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
     * Locks a user account.
     * Cannot lock the default admin account.
     *
     * @param userId UUID của user cần khóa
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable UUID userId) {
        try {
            userManagementService.deleteUser(userId);
            return ResponseEntity.ok(buildSuccessResponse(
                    "Tài khoản đã được khóa thành công.", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(buildErrorResponse(e.getMessage()));
        }
    }

    /**
     * PATCH /api/admin/users/{userId}/unlock
     * Unlocks a locked account.
     *
     * @param userId UUID của user cần mở khóa
     */
    @PatchMapping("/{userId}/unlock")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> unlockUser(@PathVariable UUID userId) {
        try {
            userManagementService.unlockUser(userId);
            return ResponseEntity.ok(buildSuccessResponse(
                    "Tài khoản đã được mở khóa thành công.", null));
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

    /**
     * PUT /api/admin/users/{userId}
     * Admin chỉnh sửa thông tin một tài khoản.
     * Chỉ các field non-null trong request body sẽ được cập nhật.
     * Không cho sửa SYSTEM_ADMIN hoặc tài khoản đã bị xoá.
     *
     * @param userId  UUID của user cần sửa
     * @param request các field cần update (null = giữ nguyên)
     */
    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable(name = "userId") UUID userId,
            @Valid @RequestBody agsfjope.backend.application.dtos.requests.user.UpdateUserRequest request) {
        try {
            UserDetailResponse result = userManagementService.updateUser(userId, request);
            return ResponseEntity.ok(buildSuccessResponse(
                    "Cập nhật tài khoản '" + result.getUsername() + "' thành công.", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(buildErrorResponse(e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ endpoints
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/admin/users
     * Lấy danh sách tất cả user (có phân trang + search + filter role).
     *
     * <p>Query params:</p>
     * <ul>
     *   <li>{@code page}     — page number, 0-indexed (default: 0)</li>
     *   <li>{@code size}     — items per page (default: 20)</li>
     *   <li>{@code sort}     — field + direction e.g. {@code createdAt,desc} (default)</li>
     *   <li>{@code search}   — partial match on username / email / fullName (optional)</li>
     *   <li>{@code roleName} — exact role filter e.g. {@code STUDENT} (optional)</li>
     * </ul>
     *
     * @return paginated list of users wrapped in standard success response
     */
    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(name = "page", defaultValue = "0")              int    page,
            @RequestParam(name = "size", defaultValue = "20")             int    size,
            @RequestParam(name = "sort", defaultValue = "createdAt,desc") String sort,
            @RequestParam(name = "search", required = false)              String search,
            @RequestParam(name = "roleName", required = false)            String roleName) {

        String[] sortParts    = sort.split(",");
        String   sortField    = sortParts[0];
        Sort.Direction dir    = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        var pageable = PageRequest.of(page, size, Sort.by(dir, sortField));

        boolean hasFilter = (search   != null && !search.isBlank())
                         || (roleName != null && !roleName.isBlank());

        Page<UserDetailResponse> result = hasFilter
                ? userManagementService.searchUsers(search, roleName, pageable)
                : userManagementService.getAllUsers(pageable);

        Map<String, Object> data = new HashMap<>();
        data.put("content",     result.getContent());
        data.put("currentPage", result.getNumber());
        data.put("totalItems",  result.getTotalElements());
        data.put("totalPages",  result.getTotalPages());
        data.put("pageSize",    result.getSize());
        data.put("isLast",      result.isLast());

        return ResponseEntity.ok(buildSuccessResponse("Lấy danh sách user thành công", data));
    }

    /**
     * GET /api/admin/users/{userId}
     * Lấy chi tiết một user theo UUID.
     *
     * @param userId UUID của user cần xem
     * @return user detail wrapped in standard success response, or 400 if not found / deleted
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable UUID userId) {
        try {
            UserDetailResponse result = userManagementService.getUserById(userId);
            return ResponseEntity.ok(buildSuccessResponse("Lấy thông tin user thành công", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(buildErrorResponse(e.getMessage()));
        }
    }


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
