package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.authservices.AuthService;
import agsfjope.backend.application.dtos.requests.auth.ForgotPasswordRequest;
import agsfjope.backend.application.dtos.requests.auth.LoginRequest;
import agsfjope.backend.application.dtos.requests.auth.RefreshTokenRequest;
import agsfjope.backend.application.dtos.requests.auth.RegisterRequest;
import agsfjope.backend.application.dtos.requests.auth.ResetPasswordRequest;
import agsfjope.backend.application.dtos.responses.auth.UserProfileResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller exposing the Authentication API endpoints.
 * <p>
 * Handles all authentication use cases: Login, Token Refresh, Logout,
 * User Profile retrieval, Forgot/Reset Password and Student Registration.
 * Business logic is fully delegated to {@link AuthService}.
 * </p>
 *
 * @see AuthService
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "APIs for login, token refresh, logout, profile, forgot password and registration")
public class AuthController {

    private final AuthService authService;

    /**
     * Đăng nhập hệ thống (SD_01_1).
     * <p>
     * Xác thực {@code username} và {@code password}; nếu hợp lệ sẽ trả về
     * một Access Token (JWT, hạn 4 tiếng) và Refresh Token (UUID, hạn 30 ngày).
     * Tài khoản chưa kích hoạt ({@code isActive = false}) hoặc bị khóa sẽ bị từ chối.
     * </p>
     *
     * @param request DTO chứa {@code username} và {@code password}
     * @return LoginResponse với {@code accessToken}, {@code refreshToken} và thông tin user
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        var loginResponse = authService.login(request);
        return ResponseEntity.ok(buildSuccessResponse("MSG-05: Đăng nhập thành công", loginResponse));
    }

    /**
     * Làm mới Access Token (SD_01_2).
     * <p>
     * Nhận một Refresh Token còn hạn, trả về Access Token mới.
     * Refresh Token hiện tại được giữ nguyên (không thay thế).
     * </p>
     *
     * @param request DTO chứa {@code refreshToken} (raw UUID string)
     * @return LoginResponse với {@code accessToken} mới và cùng {@code refreshToken} cũ
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        var loginResponse = authService.refreshToken(request);
        return ResponseEntity.ok(buildSuccessResponse("Đã cấp lại Access Token thành công", loginResponse));
    }

    /**
     * Đăng xuất tài khoản (SD_01_3).
     * <p>
     * Thu hồi toàn bộ Refresh Token của user hiện tại trong DB.
     * Access Token là stateless JWT và không bị blacklist theo thiết kế.
     * </p>
     * <p><strong>Yêu cầu xác thực:</strong> Bearer JWT hợp lệ.</p>
     *
     * @param authentication đối tượng xác thực được Spring Security inject tự động
     * @return thông báo đăng xuất thành công
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(Authentication authentication) {
        authService.logout(authentication.getName());
        return ResponseEntity.ok(buildSuccessResponse("MSG-06: Đăng xuất thành công", null));
    }

    /**
     * Lấy thông tin cá nhân (User Profile).
     * <p>
     * Trả về thông tin chi tiết của người dùng đang đăng nhập
     * (không bao gồm {@code passwordHash} và các trường nội bộ).
     * </p>
     * <p><strong>Yêu cầu xác thực:</strong> Bearer JWT hợp lệ.</p>
     *
     * @param authentication đối tượng xác thực được Spring Security inject tự động
     * @return {@link UserProfileResponse} chứa email, fullName, role, mssv, v.v.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getProfile(Authentication authentication) {
        UserProfileResponse profile = authService.getUserProfile(authentication.getName());
        return ResponseEntity.ok(buildSuccessResponse("Lấy thông tin thành công", profile));
    }

    /**
     * Quên mật khẩu — Chặng A: Gửi link đặt lại mật khẩu.
     * <p>
     * Sinh một token UUID dùng một lần (hạn 15 phút), lưu vào bảng
     * {@code PasswordResetTokens} và gửi email HTML có chứa link đặt lại đến user.
     * </p>
     * <p><strong>Public endpoint</strong> — không yêu cầu JWT.</p>
     *
     * @param request DTO chứa {@code email} của user cần khôi phục mật khẩu
     * @return thông báo yêu cầu kiểm tra email
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(buildSuccessResponse(
                "Vui lòng kiểm tra Email để nhận link khôi phục mật khẩu", null));
    }

    /**
     * Quên mật khẩu — Chặng B: Kiểm tra tính hợp lệ của token.
     * <p>
     * Frontend gọi API này ngay khi trang {@code /reset-password} được load.
     * Token được kiểm tra nhưng chưa bị đánh dấu là "đã dùng".
     * Trả về 401 nếu token không tồn tại, đã dùng hoặc hết hạn.
     * </p>
     * <p><strong>Public endpoint</strong> — không yêu cầu JWT.</p>
     *
     * @param token UUID token lấy từ query param {@code ?token=} trong link email
     * @return 200 OK nếu token hợp lệ, 401 nếu không hợp lệ / hết hạn
     */
    @GetMapping("/verify-reset-token")
    public ResponseEntity<Map<String, Object>> verifyResetToken(@RequestParam String token) {
        authService.verifyResetToken(token);
        return ResponseEntity.ok(buildSuccessResponse("Token hợp lệ. Vui lòng nhập mật khẩu mới", null));
    }

    /**
     * Quên mật khẩu — Chặng C: Xác nhận và lưu mật khẩu mới.
     * <p>
     * Kiểm tra lại token một lần nữa (phòng ngừa ngâm trang quá lâu),
     * hash mật khẩu mới bằng BCrypt, cập nhật vào DB và đánh dấu token là đã dùng.
     * </p>
     * <p><strong>Public endpoint</strong> — không yêu cầu JWT.</p>
     *
     * @param request DTO chứa {@code token}, {@code newPassword}, {@code confirmPassword}
     * @return thông báo đặt lại mật khẩu thành công
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(buildSuccessResponse(
                "Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại!", null));
    }

    /**
     * Đăng ký tài khoản sinh viên mới.
     * <p>
     * Xác thực email đuôi {@code @fpt.edu.vn}, kiểm tra chéo {@code username}
     * phải trùng bí danh email và {@code mssv} phải là 8 ký tự cuối của bí danh đó.
     * Lưu user với {@code isActive = false} và gửi link kích hoạt (JWT, hạn 24h) qua email.
     * </p>
     * <p><strong>Public endpoint</strong> — không yêu cầu JWT.</p>
     *
     * @param request DTO chứa {@code email}, {@code username}, {@code mssv}, {@code fullName}, {@code password}
     * @return thông báo yêu cầu kiểm tra hộp thư để kích hoạt tài khoản
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(buildSuccessResponse(
                "Đăng ký thành công! Email xác nhận đã được gửi đến " + request.getEmail()
                + ". Vui lòng kiểm tra hộp thư để kích hoạt tài khoản.", null));
    }

    /**
     * Kích hoạt tài khoản qua link email.
     * <p>
     * Giải mã JWT kích hoạt để lấy email, tìm user tương ứng và
     * đặt {@code isActive = true} cùng {@code emailVerifiedAt = NOW}.
     * Idempotent: nếu tài khoản đã kích hoạt thì vẫn trả về 200 OK.
     * </p>
     * <p><strong>Public endpoint</strong> — không yêu cầu JWT.</p>
     *
     * @param token JWT kích hoạt lấy từ query param {@code ?token=} trong link email
     * @return thông báo kích hoạt thành công
     */
    @GetMapping("/verify-account")
    public ResponseEntity<Map<String, Object>> verifyAccount(@RequestParam String token) {
        authService.verifyAccount(token);
        return ResponseEntity.ok(buildSuccessResponse(
                "Tài khoản đã được kích hoạt thành công. Vui lòng đăng nhập!", null));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Helper method: builds the standard success response format.
     * Format: {@code { "success": true, "message": "...", "data": {...}, "errors": null }}
     *
     * @param message human-readable success message
     * @param data    optional payload (can be null for void operations)
     * @return standardized response map
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
