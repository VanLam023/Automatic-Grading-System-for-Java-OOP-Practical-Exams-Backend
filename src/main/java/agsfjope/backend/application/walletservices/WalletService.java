package agsfjope.backend.application.walletservices;

import agsfjope.backend.application.dtos.requests.wallet.DepositRequest;
import agsfjope.backend.application.dtos.requests.wallet.ProcessWithdrawalRequest;
import agsfjope.backend.application.dtos.requests.wallet.WithdrawRequest;
import agsfjope.backend.application.dtos.responses.wallet.DepositResponse;
import agsfjope.backend.application.dtos.responses.wallet.WalletResponse;
import agsfjope.backend.application.dtos.responses.wallet.WithdrawalResponse;
import agsfjope.backend.core.entities.Wallet;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Service interface quản lý ví sinh viên.
 * <p>Các chức năng:
 * <ul>
 *   <li>Xem thông tin ví và số dư</li>
 *   <li>Nạp tiền vào ví qua PayOS</li>
 *   <li>Yêu cầu rút tiền (Admin xử lý)</li>
 *   <li>Internal: credit/debit ví cho appeal flow</li>
 * </ul>
 */
public interface WalletService {

    /**
     * Lấy thông tin ví của sinh viên (số dư + lịch sử giao dịch).
     * Tự động tạo ví nếu chưa có.
     *
     * @param studentId UUID sinh viên
     * @return thông tin ví đầy đủ
     */
    WalletResponse getMyWallet(UUID studentId);

    /**
     * Tạo lệnh nạp tiền vào ví qua PayOS.
     * Lưu Payment với type DEPOSIT. Khi webhook SUCCESS → cộng tiền ví.
     *
     * @param studentId UUID sinh viên
     * @param request   số tiền + returnUrl + cancelUrl
     * @return QR code và checkout URL từ PayOS
     */
    DepositResponse deposit(UUID studentId, DepositRequest request);

    /**
     * Yêu cầu rút tiền từ ví.
     * Tạo WithdrawalRequest + gửi Notification cho Admin.
     * Tiền bị giữ (hold) cho đến khi Admin xử lý.
     *
     * @param studentId UUID sinh viên
     * @param request   số tiền + thông tin tài khoản ngân hàng
     * @return thông tin yêu cầu rút tiền vừa tạo
     */
    WithdrawalResponse requestWithdrawal(UUID studentId, WithdrawRequest request);

    /**
     * Lấy danh sách yêu cầu rút tiền của sinh viên.
     *
     * @param studentId UUID sinh viên
     * @return danh sách yêu cầu
     */
    List<WithdrawalResponse> getMyWithdrawals(UUID studentId);

    // ─── Admin ───────────────────────────────────────────────────────────────

    /**
     * Admin lấy danh sách yêu cầu rút tiền (có filter theo status).
     *
     * @param statusFilter null = tất cả, hoặc "PENDING", "APPROVED", etc.
     * @return danh sách yêu cầu
     */
    List<WithdrawalResponse> getAllWithdrawals(String statusFilter);

    /**
     * Admin xử lý yêu cầu rút tiền (duyệt/từ chối).
     * <ul>
     *   <li>APPROVED: trừ tiền ví, gửi notification student</li>
     *   <li>REJECTED: trả lại số dư đã hold, gửi notification student</li>
     * </ul>
     *
     * @param withdrawalId UUID yêu cầu rút tiền
     * @param request      quyết định + ghi chú admin
     * @param adminId      UUID admin thực hiện
     * @return thông tin yêu cầu sau khi xử lý
     */
    WithdrawalResponse processWithdrawal(UUID withdrawalId, ProcessWithdrawalRequest request, UUID adminId);

    // ─── Internal (gọi từ AppealService, PaymentWebhookProcessor) ────────────

    /**
     * Cộng tiền vào ví (gọi sau khi Payment nạp tiền SUCCESS).
     *
     * @param studentId UUID sinh viên
     * @param amount    số tiền cộng
     * @param paymentId ID Payment tham chiếu
     */
    void creditWallet(UUID studentId, BigDecimal amount, UUID paymentId);

    /**
     * Trừ tiền ví để thanh toán phúc khảo.
     * Throw {@code IllegalStateException} nếu số dư không đủ.
     *
     * @param studentId UUID sinh viên
     * @param amount    số tiền cần trừ
     * @param appealId  ID Appeal tham chiếu
     * @return Wallet sau khi trừ tiền
     */
    Wallet debitWalletForAppeal(UUID studentId, BigDecimal amount, UUID appealId);

    /**
     * Hoàn tiền về ví khi appeal APPROVED.
     *
     * @param studentId UUID sinh viên
     * @param amount    số tiền hoàn lại
     * @param appealId  ID Appeal tham chiếu
     */
    void refundToWallet(UUID studentId, BigDecimal amount, UUID appealId);
}
