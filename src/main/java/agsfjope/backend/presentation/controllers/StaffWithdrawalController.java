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
 * REST Controller cho Exam Staff quản lý yêu cầu rút tiền của sinh viên.
 */
@RestController
@RequestMapping("/api/staff/withdrawals")
@RequiredArgsConstructor
@Slf4j
public class StaffWithdrawalController {

    private final WalletService walletService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> getAllWithdrawals(
            @RequestParam(required = false) String status) {
        log.info("[StaffWithdrawalController] Lấy danh sách withdrawal, status={}", status);
        List<WithdrawalResponse> response = walletService.getAllWithdrawals(status);
        return ResponseEntity.ok(buildResponse(true, "Lấy danh sách yêu cầu rút tiền thành công.", response));
    }

    @PutMapping("/{withdrawalId}/process")
    @PreAuthorize("hasAnyAuthority('EXAM_STAFF', 'ROLE_EXAM_STAFF')")
    public ResponseEntity<Map<String, Object>> processWithdrawal(
            @PathVariable UUID withdrawalId,
            @Valid @RequestBody ProcessWithdrawalRequest request,
            Authentication authentication) {
        UUID staffId = extractUserId(authentication);
        log.info("[StaffWithdrawalController] Staff {} xử lý withdrawal {}, approved={}",
                staffId, withdrawalId, request.getIsApproved());

        WithdrawalResponse response = walletService.processWithdrawal(withdrawalId, request, staffId);
        String msg = Boolean.TRUE.equals(request.getIsApproved())
                ? "Đã duyệt yêu cầu rút tiền thành công."
                : "Đã từ chối yêu cầu rút tiền.";
        return ResponseEntity.ok(buildResponse(true, msg, response));
    }

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
