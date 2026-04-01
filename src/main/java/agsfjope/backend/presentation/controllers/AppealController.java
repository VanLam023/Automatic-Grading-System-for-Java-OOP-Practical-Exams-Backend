package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.appealservices.AppealService;
import agsfjope.backend.application.dtos.requests.appeal.CreateAppealRequest;
import agsfjope.backend.application.dtos.responses.appeal.CreateAppealResponse;
import agsfjope.backend.infrastructure.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller xử lý các request liên quan đến đơn phúc khảo (Appeal).
 *
 * <p>Endpoint hiện tại:</p>
 * <ul>
 *   <li>{@code POST /api/v1/student/appeals} — Sinh viên tạo đơn phúc khảo mới</li>
 * </ul>
 *
 * <p>Authorization: chỉ {@code STUDENT} mới được phép tạo đơn phúc khảo.</p>
 */
@RestController
@RequestMapping("/api/v1/student/appeals")
@RequiredArgsConstructor
@Slf4j
public class AppealController {

    private final AppealService appealService;

    /**
     * Tạo đơn phúc khảo mới cho một bài nộp đã được chấm điểm.
     *
     * <p>Luồng xử lý:</p>
     * <ol>
     *   <li>Validate request body</li>
     *   <li>Kiểm tra business rules (BR-01, BR-02, BR-03)</li>
     *   <li>Tạo Appeal {@code PENDING_PAYMENT} + Payment + gọi PayOS</li>
     *   <li>Trả về QR code và checkout URL để Frontend hiển thị màn hình thanh toán</li>
     * </ol>
     *
     * @param request        thông tin đơn phúc khảo (submissionId, reason)
     * @param authentication JWT principal của sinh viên đang đăng nhập
     * @return thông tin appeal + link thanh toán PayOS (HTTP 201)
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('STUDENT', 'ROLE_STUDENT')")
    public ResponseEntity<Map<String, Object>> createAppeal(
            @Valid @RequestBody CreateAppealRequest request,
            Authentication authentication) {

        UUID studentId = extractUserId(authentication);
        log.info("[AppealController] Student {} tạo appeal cho submission {}",
                studentId, request.getSubmissionId());

        CreateAppealResponse response = appealService.createAppeal(studentId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(buildResponse(true, "Tạo đơn phúc khảo thành công. Vui lòng chờ kết quả trong thời gian quy định.", response));
    }

    /**
     * Lấy danh sách đơn phúc khảo của sinh viên đang đăng nhập.
     * Trả về overview stats (tổng, đang xử lý, đã chấp nhận, đã từ chối)
     * và danh sách chi tiết từng đơn, sắp xếp mới nhất trước.
     *
     * @param authentication JWT principal
     * @return trang My Appeals (HTTP 200)
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('STUDENT', 'ROLE_STUDENT')")
    public ResponseEntity<Map<String, Object>> getMyAppeals(Authentication authentication) {
        UUID studentId = extractUserId(authentication);
        var response = appealService.getMyAppeals(studentId);
        return ResponseEntity.ok(buildResponse(true, "Lấy danh sách phúc khảo thành công.", response));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Lấy UUID sinh viên từ JWT principal.
     */
    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("Không tìm thấy thông tin xác thực. Vui lòng đăng nhập lại.");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getUser().getUserId();
        }
        throw new IllegalStateException("Không thể xác định danh tính người dùng từ token.");
    }

    /**
     * Build response chuẩn của project:
     * {@code { "success": true/false, "message": "...", "data": {...}, "errors": null }}
     */
    private Map<String, Object> buildResponse(boolean success, String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("data", data);
        response.put("errors", null);
        return response;
    }
}
