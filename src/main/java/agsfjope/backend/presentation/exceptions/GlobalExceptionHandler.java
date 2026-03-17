package agsfjope.backend.presentation.exceptions;

import agsfjope.backend.core.exceptions.auth.AccountLockedException;
import agsfjope.backend.core.exceptions.auth.AccountNotVerifiedException;
import agsfjope.backend.core.exceptions.auth.InvalidTokenException;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.exceptions.auth.TokenExpiredException;
import agsfjope.backend.core.exceptions.auth.UnauthorizedException;
import agsfjope.backend.core.exceptions.config.ConfigNotFoundException;
import agsfjope.backend.core.exceptions.config.InvalidConfigException;
import agsfjope.backend.core.exceptions.exam.ExamConflictException;
import agsfjope.backend.core.exceptions.notification.NotificationNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global Exception Handler to catch and format all exceptions thrown anywhere in the app.
 * Ensures that API clients ALWAYS receive a consistent JSON response format:
 * { "success": false, "message": "...", "data": null, "errors": "..." }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Handle Invalid Credentials (username not found / password mismatch)
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(buildErrorResponse(ex.getMessage()));
    }

    // 2. Handle Account Not Verified (not active)
    @ExceptionHandler(AccountNotVerifiedException.class)
    public ResponseEntity<Map<String, Object>> handleNotVerified(AccountNotVerifiedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(buildErrorResponse(ex.getMessage()));
    }

    // 3. Handle Account Locked
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Map<String, Object>> handleLocked(AccountLockedException ex) {
        return ResponseEntity
                .status(HttpStatus.LOCKED)
                .body(buildErrorResponse(ex.getMessage()));
    }

    // 4. Handle Not Found (resource does not exist in DB)
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(buildErrorResponse(ex.getMessage()));
    }

    // 4.1 Handle Config Not Found (system config key or grading mode not found)
    @ExceptionHandler(ConfigNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleConfigNotFound(ConfigNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(buildErrorResponse(ex.getMessage()));
    }

    // 5. Handle Invalid Refresh Token (not found in DB or already revoked)
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidToken(InvalidTokenException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(buildErrorResponse(ex.getMessage()));
    }

    // 6. Handle Expired Refresh Token / Reset Token
    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleTokenExpired(TokenExpiredException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(buildErrorResponse(ex.getMessage()));
    }

    // 7. Handle Validation Errors (e.g. @NotBlank, @Pattern, @AssertTrue on DTOs)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse("Dữ liệu không hợp lệ: " + errors));
    }

    // 8. Handle Business-rule validation failures during registration (e.g. username/MSSV mismatch)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(ex.getMessage()));
    }

    // 8.1 Handle invalid system configuration payloads
    @ExceptionHandler(InvalidConfigException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidConfig(InvalidConfigException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildErrorResponse(ex.getMessage()));
    }

    // 8.15 Handle exam deletion conflict (exam has existing submissions — BR-12)
    @ExceptionHandler(ExamConflictException.class)
    public ResponseEntity<Map<String, Object>> handleExamConflict(ExamConflictException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(buildErrorResponse(ex.getMessage()));
    }

    // 8.2 Handle missing/invalid authentication context (often when Bearer token missing/invalid)
    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationCredentialsNotFound(AuthenticationCredentialsNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(buildErrorResponse("Bạn chưa đăng nhập hoặc token không hợp lệ"));
    }

    // 8.3 Handle access denied due to insufficient role/authority
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(buildErrorResponse("Bạn không có quyền truy cập tài nguyên này"));
    }

    // 9. Handle Notification Not Found (MSG-82)
    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotificationNotFound(NotificationNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(buildErrorResponse(ex.getMessage()));
    }

    // 10. Handle Generic/Unexpected Internal Server Errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse("Lỗi hệ thống không xác định: " + ex.getMessage()));
    }

    /**
     * Helper method to standardize the error response format.
     */
    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("data", null);
        response.put("errors", message);
        return response;
    }
}
