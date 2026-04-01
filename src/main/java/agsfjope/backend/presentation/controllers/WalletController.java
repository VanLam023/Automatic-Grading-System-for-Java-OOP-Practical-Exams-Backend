package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.dtos.requests.wallet.DepositRequest;
import agsfjope.backend.application.dtos.requests.wallet.WithdrawRequest;
import agsfjope.backend.application.dtos.responses.wallet.DepositResponse;
import agsfjope.backend.application.dtos.responses.wallet.WalletResponse;
import agsfjope.backend.application.dtos.responses.wallet.WithdrawalResponse;
import agsfjope.backend.application.walletservices.WalletService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller quản lý ví điện tử của sinh viên.
 *
 * <p>
 * Các endpoint:
 * <ul>
 * <li>{@code GET  /api/v1/student/wallet} — Xem thông tin ví + lịch sử giao
 * dịch</li>
 * <li>{@code POST /api/v1/student/wallet/deposit} — Nạp tiền vào ví qua
 * PayOS</li>
 * <li>{@code POST /api/v1/student/wallet/withdraw} — Yêu cầu rút tiền</li>
 * <li>{@code GET  /api/v1/student/wallet/withdrawals} — Lịch sử yêu cầu rút
 * tiền</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/student/wallet")
@RequiredArgsConstructor
@Slf4j
public class WalletController {

    private final WalletService walletService;

    /**
     * Xem thông tin ví (số dư + lịch sử giao dịch).
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('STUDENT', 'ROLE_STUDENT')")
    public ResponseEntity<Map<String, Object>> getMyWallet(Authentication authentication) {
        UUID studentId = extractUserId(authentication);
        log.info("[WalletController] Student {} xem ví", studentId);
        WalletResponse response = walletService.getMyWallet(studentId);
        return ResponseEntity.ok(buildResponse(true, "Lấy thông tin ví thành công.", response));
    }

    /**
     * Nạp tiền vào ví qua PayOS.
     * Trả về QR code và checkout URL.
     */
    @PostMapping("/deposit")
    @PreAuthorize("hasAnyAuthority('STUDENT', 'ROLE_STUDENT')")
    public ResponseEntity<Map<String, Object>> deposit(
            @Valid @RequestBody DepositRequest request,
            Authentication authentication) {
        UUID studentId = extractUserId(authentication);
        log.info("[WalletController] Student {} nạp {} VND", studentId, request.getAmount());
        DepositResponse response = walletService.deposit(studentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buildResponse(true, "Tạo lệnh nạp tiền thành công. Vui lòng quét QR để hoàn tất.", response));
    }

    /**
     * Yêu cầu rút tiền từ ví.
     * Admin sẽ nhận notification và xử lý.
     */
    @PostMapping("/withdraw")
    @PreAuthorize("hasAnyAuthority('STUDENT', 'ROLE_STUDENT')")
    public ResponseEntity<Map<String, Object>> requestWithdrawal(
            @Valid @RequestBody WithdrawRequest request,
            Authentication authentication) {
        UUID studentId = extractUserId(authentication);
        log.info("[WalletController] Student {} yêu cầu rút {} VND", studentId, request.getAmount());
        WithdrawalResponse response = walletService.requestWithdrawal(studentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buildResponse(true, "Yêu cầu rút tiền đã được gửi. Admin sẽ xử lý sớm nhất có thể.", response));
    }

    /**
     * Xem lịch sử yêu cầu rút tiền của sinh viên.
     */
    @GetMapping("/withdrawals")
    @PreAuthorize("hasAnyAuthority('STUDENT', 'ROLE_STUDENT')")
    public ResponseEntity<Map<String, Object>> getMyWithdrawals(Authentication authentication) {
        UUID studentId = extractUserId(authentication);
        List<WithdrawalResponse> response = walletService.getMyWithdrawals(studentId);
        return ResponseEntity.ok(buildResponse(true, "Lấy lịch sử rút tiền thành công.", response));
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
