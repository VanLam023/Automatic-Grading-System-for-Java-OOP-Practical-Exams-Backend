package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.requests.wallet.ProcessWithdrawalRequest;
import agsfjope.backend.application.dtos.responses.wallet.WithdrawalResponse;
import agsfjope.backend.application.walletservices.WalletService;
import agsfjope.backend.infrastructure.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller cho Admin quản lý yêu cầu rút tiền của sinh viên.
 *
 * <p>Các endpoint:
 * <ul>
 *   <li>{@code GET  /api/v1/admin/withdrawals}           — Danh sách yêu cầu (có filter status)</li>
 *   <li>{@code PUT  /api/v1/admin/withdrawals/{id}/process} — Duyệt / Từ chối</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/admin/withdrawals")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN', 'ROLE_SYSTEM_ADMIN', 'EXAM_STAFF', 'ROLE_EXAM_STAFF')")
public class AdminWithdrawalController {

    private final WalletService walletService;

    /**
     * Lấy danh sách yêu cầu rút tiền.
     *
     * @param status filter theo status (PENDING, APPROVED, REJECTED, COMPLETED). Không truyền = tất cả.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllWithdrawals(
            @RequestParam(required = false) String status) {
        log.info("[AdminWithdrawalController] Lấy danh sách withdrawal, status={}", status);
        List<WithdrawalResponse> response = walletService.getAllWithdrawals(status);
        return ResponseEntity.ok(buildResponse(true, "Lấy danh sách yêu cầu rút tiền thành công.", response));
    }

    /**
     * Duyệt hoặc từ chối yêu cầu rút tiền.
     *
     * @param withdrawalId UUID yêu cầu rút tiền
     * @param request      { isApproved: true/false, adminNote: "..." }
     */
    @PutMapping("/{withdrawalId}/process")
    public ResponseEntity<Map<String, Object>> processWithdrawal(
            @PathVariable UUID withdrawalId,
            @Valid @RequestBody ProcessWithdrawalRequest request,
            Authentication authentication) {
        UUID adminId = extractUserId(authentication);
        log.info("[AdminWithdrawalController] Admin {} xử lý withdrawal {}, approved={}",
                adminId, withdrawalId, request.getIsApproved());
        WithdrawalResponse response = walletService.processWithdrawal(withdrawalId, request, adminId);
        String msg = Boolean.TRUE.equals(request.getIsApproved())
                ? "Đã duyệt yêu cầu rút tiền thành công."
                : "Đã từ chối yêu cầu rút tiền.";
        return ResponseEntity.ok(buildResponse(true, msg, response));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

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

    private Map<String, Object> buildResponse(boolean success, String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("data", data);
        response.put("errors", null);
        return response;
    }
}
