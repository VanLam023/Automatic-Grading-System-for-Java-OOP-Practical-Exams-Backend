package agsfjope.backend.application.walletservices.impl;

import agsfjope.backend.application.dtos.requests.wallet.DepositRequest;
import agsfjope.backend.application.dtos.requests.wallet.ProcessWithdrawalRequest;
import agsfjope.backend.application.dtos.requests.wallet.WithdrawRequest;
import agsfjope.backend.application.dtos.responses.wallet.DepositResponse;
import agsfjope.backend.application.dtos.responses.wallet.WalletResponse;
import agsfjope.backend.application.dtos.responses.wallet.WalletTransactionResponse;
import agsfjope.backend.application.dtos.responses.wallet.WithdrawalResponse;
import agsfjope.backend.application.notificationservices.NotificationService;
import agsfjope.backend.application.ports.out.PaymentGatewayPort;
import agsfjope.backend.application.walletservices.WalletService;
import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.enums.WalletTransactionType;
import agsfjope.backend.core.enums.WithdrawalStatus;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.payment.PaymentRepository;
import agsfjope.backend.core.repositories.wallet.WalletRepository;
import agsfjope.backend.core.repositories.wallet.WalletTransactionRepository;
import agsfjope.backend.core.repositories.wallet.WithdrawalRequestRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation của {@link WalletService}.
 *
 * <p>Điều phối các thao tác ví: nạp tiền, rút tiền,
 * trừ/hoàn tiền cho luồng phúc khảo.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {

    private static final String KEY_PAYMENT_TIMEOUT_MINUTES = "APPEAL_PAYMENT_TIMEOUT_MINUTES";
    private static final String KEY_APPEAL_FEE = "APPEAL_FEE";
    private static final int DEFAULT_TIMEOUT_MIN = 15;
    private static final BigDecimal DEFAULT_APPEAL_FEE = new BigDecimal("200000");

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final NotificationService notificationService;
    private final SystemConfigRepository systemConfigRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Xem ví
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public WalletResponse getMyWallet(UUID studentId) {
        log.info("[Wallet] Student {} xem thông tin ví", studentId);
        reconcilePendingWalletDeposits(studentId);

        BigDecimal appealFee = loadAppealFee();
        Optional<Wallet> walletOptional = walletRepository.findByStudentId(studentId);
        if (walletOptional.isEmpty()) {
            return WalletResponse.builder()
                    .hasWallet(false)
                    .walletId(null)
                    .balance(BigDecimal.ZERO)
                    .appealFee(appealFee)
                    .createdAt(null)
                    .updatedAt(null)
                    .transactions(List.of())
                    .build();
        }

        Wallet wallet = walletOptional.get();
        List<WalletTransactionResponse> txList = walletTransactionRepository
                .findByWalletIdOrderByCreatedAtDesc(wallet.getWalletId())
                .stream()
                .map(this::toTransactionResponse)
                .toList();

        return WalletResponse.builder()
                .hasWallet(true)
                .walletId(wallet.getWalletId())
                .balance(wallet.getBalance())
                .appealFee(appealFee)
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .transactions(txList)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Nạp tiền vào ví
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DepositResponse deposit(UUID studentId, DepositRequest request) {
        log.info("[Wallet] Student {} nạp tiền {} VND", studentId, request.getAmount());

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sinh viên: " + studentId));

        long orderCode = System.currentTimeMillis() / 1000L;
        int timeoutMinutes = loadTimeoutMinutes();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(timeoutMinutes);

        // ① Lưu Payment vào DB TRƯỚC khi gọi PayOS
        //    → Đảm bảo orderCode luôn tồn tại trong DB dù PayOS timeout
        Payment payment = Payment.builder()
                .appeal(null)
                .student(student)
                .depositForStudent(student)
                .paymentPurpose("WALLET_DEPOSIT")
                .amount(request.getAmount())
                .currency("VND")
                .payosOrderId(String.valueOf(orderCode))
                .expiresAt(expiresAt)
                .build();
        payment = paymentRepository.save(payment);
        log.info("[Wallet] Đã lưu payment WALLET_DEPOSIT, orderCode={}, paymentId={}", orderCode, payment.getPaymentId());

        // ② Gọi PayOS tạo link thanh toán — nội dung chuyển khoản = MSSV
        String mssv = student.getMssv() != null ? student.getMssv() : "SV" + orderCode;
        String description = mssv;
        try {
            PaymentGatewayPort.PaymentLinkResult result = paymentGatewayPort.createPaymentLink(
                    orderCode, request.getAmount(), description,
                    request.getReturnUrl(), request.getCancelUrl());

            // Cập nhật Payment với thông tin PayOS
            payment.setPayosPaymentLinkId(result.paymentLinkId());
            payment.setQrCodeUrl(result.qrCodeUrl());
            payment.setCheckoutUrl(result.checkoutUrl());
            paymentRepository.save(payment);
            log.info("[Wallet] PayOS tạo link thành công, orderCode={}", orderCode);

            return DepositResponse.builder()
                    .depositPaymentId(payment.getPaymentId())
                    .payosOrderId(String.valueOf(orderCode))
                    .amount(request.getAmount())
                    .currency("VND")
                    .qrCodeUrl(result.qrCodeUrl())
                    .checkoutUrl(result.checkoutUrl())
                    .expiresAt(expiresAt)
                    .build();

        } catch (Exception e) {
            // PayOS timeout / không accessible: Payment đã lưu DB, trả về orderCode để simulate
            log.warn("[Wallet] PayOS không phản hồi ({}). Payment đã lưu DB với orderCode={}. Dùng /dev/simulate-success để test.",
                    e.getMessage(), orderCode);

            return DepositResponse.builder()
                    .depositPaymentId(payment.getPaymentId())
                    .payosOrderId(String.valueOf(orderCode))
                    .amount(request.getAmount())
                    .currency("VND")
                    .qrCodeUrl(null)        // PayOS chưa trả về
                    .checkoutUrl(null)
                    .expiresAt(expiresAt)
                    .payosError("[DEV] PayOS không accessible. Dùng payosOrderId=" + orderCode
                            + " với endpoint /api/v1/payments/dev/simulate-success để hoàn tất nạp tiền.")
                    .build();
        }
    }


    // ─────────────────────────────────────────────────────────────────────────
    // 3. Rút tiền (yêu cầu)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public WithdrawalResponse requestWithdrawal(UUID studentId, WithdrawRequest request) {
        log.info("[Wallet] Student {} yêu cầu rút {} VND", studentId, request.getAmount());

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sinh viên: " + studentId));

        Wallet wallet = walletRepository.findByStudentId(studentId)
                .orElseThrow(() -> new IllegalStateException("Bạn chưa có ví. Vui lòng nạp tiền trước."));

        // Kiểm tra số dư đủ không
        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new IllegalStateException(
                    String.format("Số dư ví không đủ. Số dư hiện tại: %,.0f VND, số tiền muốn rút: %,.0f VND",
                            wallet.getBalance(), request.getAmount()));
        }

        // Tạo WithdrawalRequest
        WithdrawalRequest withdrawalRequest = WithdrawalRequest.builder()
                .wallet(wallet)
                .student(student)
                .amount(request.getAmount())
                .bankName(request.getBankName())
                .accountNumber(request.getAccountNumber())
                .accountHolder(request.getAccountHolder())
                .status(WithdrawalStatus.PENDING)
                .build();
        withdrawalRequest = withdrawalRequestRepository.save(withdrawalRequest);
        log.info("[Wallet] Tạo WithdrawalRequest thành công: {}", withdrawalRequest.getWithdrawalId());

        // Gửi notification cho tất cả Admin/EXAM_STAFF (SYSTEM_ADMIN)
        notifyAdminsWithdrawal(student, request.getAmount(), withdrawalRequest.getWithdrawalId());

        return toWithdrawalResponse(withdrawalRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WithdrawalResponse> getMyWithdrawals(UUID studentId) {
        return withdrawalRequestRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
                .stream()
                .map(this::toWithdrawalResponse)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Admin xử lý rút tiền
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<WithdrawalResponse> getAllWithdrawals(String statusFilter) {
        WithdrawalStatus status = null;
        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                status = WithdrawalStatus.valueOf(statusFilter.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Trạng thái không hợp lệ: " + statusFilter);
            }
        }
        return withdrawalRequestRepository.findAllByStatus(status)
                .stream()
                .map(this::toWithdrawalResponseWithStudent)
                .toList();
    }

    @Override
    @Transactional
    public WithdrawalResponse processWithdrawal(UUID withdrawalId, ProcessWithdrawalRequest request, UUID adminId) {
        log.info("[Wallet] Admin {} xử lý withdrawal {}, approved={}",
                adminId, withdrawalId, request.getIsApproved());

        WithdrawalRequest wr = withdrawalRequestRepository.findById(withdrawalId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy yêu cầu rút tiền: " + withdrawalId));

        if (wr.getStatus() != WithdrawalStatus.PENDING) {
            throw new IllegalStateException(
                    "Yêu cầu này đã được xử lý. Trạng thái hiện tại: " + wr.getStatus());
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy admin: " + adminId));

        Wallet wallet = wr.getWallet();
        UUID studentId = wr.getStudent().getUserId();

        if (Boolean.TRUE.equals(request.getIsApproved())) {
            // Kiểm tra lại số dư trước khi trừ
            if (wallet.getBalance().compareTo(wr.getAmount()) < 0) {
                throw new IllegalStateException("Số dư ví không đủ để thực hiện rút tiền");
            }

            // Trừ tiền ví
            BigDecimal before = wallet.getBalance();
            int rows = walletRepository.adjustBalance(wallet.getWalletId(), wr.getAmount().negate());
            if (rows == 0) {
                throw new IllegalStateException("Không thể trừ tiền ví (có thể do Race Condition). Vui lòng thử lại.");
            }

            // Refresh balance
            BigDecimal after = before.subtract(wr.getAmount());

            // Ghi transaction
            saveTransaction(wallet, WalletTransactionType.WITHDRAWAL, wr.getAmount(),
                    before, after, wr.getWithdrawalId(), "WITHDRAWAL",
                    "Rút tiền về tài khoản " + wr.getBankName() + " - " + wr.getAccountNumber());

            wr.setStatus(WithdrawalStatus.APPROVED);
            log.info("[Wallet] Withdrawal {} đã APPROVED, trừ {} VND từ ví student {}",
                    withdrawalId, wr.getAmount(), studentId);

            // Thông báo cho student
            notificationService.createNotification(
                    studentId,
                    "Yêu cầu rút tiền được duyệt",
                    String.format("Yêu cầu rút %,.0f VND của bạn đã được Admin duyệt. Tiền sẽ được chuyển đến tài khoản %s - %s.",
                            wr.getAmount(), wr.getBankName(), wr.getAccountNumber()),
                    "WITHDRAWAL", wr.getWithdrawalId());

        } else {
            // Từ chối: giữ nguyên số dư
            wr.setStatus(WithdrawalStatus.REJECTED);
            wr.setAdminNote(request.getAdminNote());
            log.info("[Wallet] Withdrawal {} đã REJECTED", withdrawalId);

            notificationService.createNotification(
                    studentId,
                    "Yêu cầu rút tiền bị từ chối",
                    String.format("Yêu cầu rút %,.0f VND của bạn đã bị từ chối. Lý do: %s",
                            wr.getAmount(),
                            request.getAdminNote() != null ? request.getAdminNote() : "Không có ghi chú"),
                    "WITHDRAWAL", wr.getWithdrawalId());
        }

        wr.setProcessedBy(admin);
        wr.setProcessedAt(OffsetDateTime.now());
        wr.setAdminNote(request.getAdminNote());
        withdrawalRequestRepository.save(wr);

        return toWithdrawalResponseWithStudent(wr);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Internal methods (gọi từ PaymentWebhookProcessor, AppealService)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void creditWallet(UUID studentId, BigDecimal amount, UUID paymentId) {
        log.info("[Wallet] Cộng {} VND vào ví student {}", amount, studentId);
        Wallet wallet = getOrCreateWallet(studentId);
        BigDecimal before = wallet.getBalance();

        walletRepository.adjustBalance(wallet.getWalletId(), amount);
        BigDecimal after = before.add(amount);

        saveTransaction(wallet, WalletTransactionType.DEPOSIT, amount,
                before, after, paymentId, "PAYMENT",
                "Nạp tiền vào ví qua PayOS");
    }

    @Override
    @Transactional
    public Wallet debitWalletForAppeal(UUID studentId, BigDecimal amount, UUID appealId) {
        log.info("[Wallet] Trừ {} VND từ ví student {} cho appeal {}", amount, studentId, appealId);
        Wallet wallet = walletRepository.findByStudentId(studentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Bạn chưa có ví. Vui lòng nạp tiền trước khi tạo đơn phúc khảo."));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException(
                    String.format("Số dư ví không đủ để thanh toán phí phúc khảo. " +
                            "Số dư hiện tại: %,.0f VND, phí phúc khảo: %,.0f VND. " +
                            "Vui lòng nạp thêm tiền vào ví.",
                            wallet.getBalance(), amount));
        }

        BigDecimal before = wallet.getBalance();
        int rows = walletRepository.adjustBalance(wallet.getWalletId(), amount.negate());
        if (rows == 0) {
            throw new IllegalStateException("Không thể trừ tiền ví. Vui lòng thử lại.");
        }
        BigDecimal after = before.subtract(amount);

        saveTransaction(wallet, WalletTransactionType.APPEAL_PAYMENT, amount,
                before, after, appealId, "APPEAL",
                "Thanh toán phí phúc khảo");

        // Refresh wallet object
        wallet.setBalance(after);
        return wallet;
    }

    @Override
    @Transactional
    public void refundToWallet(UUID studentId, BigDecimal amount, UUID appealId) {
        log.info("[Wallet] Hoàn {} VND về ví student {} cho appeal {}", amount, studentId, appealId);
        Wallet wallet = getOrCreateWallet(studentId);
        BigDecimal before = wallet.getBalance();

        walletRepository.adjustBalance(wallet.getWalletId(), amount);
        BigDecimal after = before.add(amount);

        saveTransaction(wallet, WalletTransactionType.APPEAL_REFUND, amount,
                before, after, appealId, "APPEAL",
                "Hoàn tiền phúc khảo được chấp thuận");

        // Thông báo cho student
        notificationService.createNotification(
                studentId,
                "Hoàn tiền phúc khảo",
                String.format("Phúc khảo của bạn đã được APPROVED. %,.0f VND đã được hoàn lại vào ví.", amount),
                "APPEAL", appealId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tự đối soát các lệnh nạp ví còn PENDING với PayOS khi student mở ví.
     *
     * <p>
     * Luồng này giúp hệ thống vẫn cộng tiền vào ví ngay cả khi webhook PayOS bị chậm,
     * không về được local, hoặc student vừa thanh toán xong rồi quay lại trang ví.
     * </p>
     *
     * <p>
     * Idempotency được giữ bằng cách chỉ cho phép Payment chuyển từ PENDING -> SUCCESS
     * đúng một lần ở tầng repository. Chỉ lần cập nhật thành công mới được cộng ví.
     * </p>
     */
    private void reconcilePendingWalletDeposits(UUID studentId) {
        List<Payment> pendingDeposits = paymentRepository.findPendingWalletDepositsByStudentId(studentId);
        if (pendingDeposits.isEmpty()) {
            return;
        }

        log.info("[Wallet] Tìm thấy {} lệnh WALLET_DEPOSIT còn PENDING để đối soát cho student {}",
                pendingDeposits.size(), studentId);

        OffsetDateTime now = OffsetDateTime.now();

        for (Payment payment : pendingDeposits) {
            try {
                String payosLookupId = payment.getPayosOrderId() != null && !payment.getPayosOrderId().isBlank()
                        ? payment.getPayosOrderId()
                        : payment.getPayosPaymentLinkId();

                if (payosLookupId == null || payosLookupId.isBlank()) {
                    log.warn("[Wallet] Payment {} thiếu cả payosOrderId lẫn paymentLinkId, bỏ qua đối soát",
                            payment.getPaymentId());
                    continue;
                }

                PaymentGatewayPort.PaymentLinkInfo paymentInfo = paymentGatewayPort.getPaymentLinkInfo(payosLookupId);
                String payosStatus = paymentInfo.status() != null
                        ? paymentInfo.status().trim().toUpperCase()
                        : "UNKNOWN";

                log.info("[Wallet] Đối soát payment {} / orderCode {} -> PayOS status={}",
                        payment.getPaymentId(), payosLookupId, payosStatus);

                if ("PAID".equals(payosStatus)) {
                    int updatedRows = paymentRepository.markSuccessIfPending(
                            payment.getPaymentId(),
                            now,
                            paymentInfo.rawResponse()
                    );

                    if (updatedRows == 0) {
                        log.info("[Wallet] Payment {} đã được xử lý trước đó, bỏ qua cộng ví trùng", payment.getPaymentId());
                        continue;
                    }

                    UUID recipientId = payment.getDepositForStudent() != null
                            ? payment.getDepositForStudent().getUserId()
                            : payment.getStudent().getUserId();

                    creditWallet(recipientId, payment.getAmount(), payment.getPaymentId());
                    log.info("[Wallet] Đối soát thành công payment {} -> đã cộng {} VND vào ví student {}",
                            payment.getPaymentId(), payment.getAmount(), recipientId);
                    continue;
                }

                if ("CANCELLED".equals(payosStatus)) {
                    int updatedRows = paymentRepository.markFailedIfPending(payment.getPaymentId());
                    if (updatedRows > 0) {
                        log.info("[Wallet] Payment {} bị PayOS báo CANCELLED -> chuyển FAILED", payment.getPaymentId());
                    }
                    continue;
                }

                if (payment.getExpiresAt() != null && payment.getExpiresAt().isBefore(now)
                        && ("PENDING".equals(payosStatus) || "UNKNOWN".equals(payosStatus))) {
                    int updatedRows = paymentRepository.markFailedIfPending(payment.getPaymentId());
                    if (updatedRows > 0) {
                        log.info("[Wallet] Payment {} đã quá hạn mà chưa thanh toán -> chuyển FAILED", payment.getPaymentId());
                    }
                }
            } catch (Exception e) {
                log.warn("[Wallet] Không thể đối soát payment {} lúc student mở ví: {}",
                        payment.getPaymentId(), e.getMessage());
            }
        }
    }

    /**
     * Lấy ví của student. Nếu chưa có thì tạo mới.
     */
    private Wallet getOrCreateWallet(UUID studentId) {
        log.info("[Wallet] Bắt đầu tìm ví cho studentId: {}", studentId);
        
        Optional<Wallet> existing = walletRepository.findByStudentId(studentId);
        
        if (existing.isPresent()) {
            Wallet w = existing.get();
            log.info("[Wallet] Đã tìm thấy ví cũ: walletId={}, studentId={}", w.getWalletId(), studentId);
            return w;
        }

        log.info("[Wallet] Không tìm thấy ví cũ -> Đang tạo ví mới cho studentId: {}", studentId);
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Sinh viên không tồn tại: " + studentId));
        
        Wallet newWallet = Wallet.builder()
                .student(student)
                .balance(BigDecimal.ZERO)
                .build();
        
        Wallet saved = walletRepository.save(newWallet);
        log.info("[Wallet] Đã lưu ví mới vào DB: walletId={}, studentId={}", saved.getWalletId(), studentId);
        return saved;
    }

    /**
     * Ghi lại giao dịch ví.
     */
    private void saveTransaction(Wallet wallet, WalletTransactionType type, BigDecimal amount,
                                  BigDecimal before, BigDecimal after,
                                  UUID referenceId, String referenceType, String description) {
        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .type(type)
                .amount(amount)
                .balanceBefore(before)
                .balanceAfter(after)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .description(description)
                .build();
        walletTransactionRepository.save(tx);
    }

    /**
     * Gửi notification cho tất cả SYSTEM_ADMIN khi có yêu cầu rút tiền.
     */
    private void notifyAdminsWithdrawal(User student, BigDecimal amount, UUID withdrawalId) {
        List<agsfjope.backend.core.entities.User> admins =
                userRepository.findByRole_NameAndDeletedAtIsNull("SYSTEM_ADMIN");
        for (User admin : admins) {
            notificationService.createNotification(
                    admin.getUserId(),
                    "Yêu cầu rút tiền mới",
                    String.format("Sinh viên %s (%s) yêu cầu rút %,.0f VND. Vui lòng xem xét và phê duyệt.",
                            student.getFullName(), student.getMssv(), amount),
                    "WITHDRAWAL", withdrawalId);
        }
        log.info("[Wallet] Đã gửi notification cho {} admin về withdrawal {}", admins.size(), withdrawalId);
    }

    private BigDecimal loadAppealFee() {
        return systemConfigRepository.findByConfigKey(KEY_APPEAL_FEE)
                .map(c -> {
                    try { return new BigDecimal(c.getConfigValue()); }
                    catch (NumberFormatException e) { return DEFAULT_APPEAL_FEE; }
                })
                .orElse(DEFAULT_APPEAL_FEE);
    }

    private int loadTimeoutMinutes() {
        return systemConfigRepository.findByConfigKey(KEY_PAYMENT_TIMEOUT_MINUTES)
                .map(c -> {
                    try { return Integer.parseInt(c.getConfigValue()); }
                    catch (NumberFormatException e) { return DEFAULT_TIMEOUT_MIN; }
                })
                .orElse(DEFAULT_TIMEOUT_MIN);
    }

    private WalletTransactionResponse toTransactionResponse(WalletTransaction tx) {
        return WalletTransactionResponse.builder()
                .transactionId(tx.getTransactionId())
                .type(tx.getType())
                .amount(tx.getAmount())
                .balanceBefore(tx.getBalanceBefore())
                .balanceAfter(tx.getBalanceAfter())
                .description(tx.getDescription())
                .referenceId(tx.getReferenceId())
                .referenceType(tx.getReferenceType())
                .createdAt(tx.getCreatedAt())
                .build();
    }

    private WithdrawalResponse toWithdrawalResponse(WithdrawalRequest wr) {
        return WithdrawalResponse.builder()
                .withdrawalId(wr.getWithdrawalId())
                .amount(wr.getAmount())
                .bankName(wr.getBankName())
                .accountNumber(wr.getAccountNumber())
                .accountHolder(wr.getAccountHolder())
                .status(wr.getStatus())
                .adminNote(wr.getAdminNote())
                .processedByName(wr.getProcessedBy() != null ? wr.getProcessedBy().getFullName() : null)
                .processedAt(wr.getProcessedAt())
                .createdAt(wr.getCreatedAt())
                .build();
    }

    private WithdrawalResponse toWithdrawalResponseWithStudent(WithdrawalRequest wr) {
        return WithdrawalResponse.builder()
                .withdrawalId(wr.getWithdrawalId())
                .amount(wr.getAmount())
                .bankName(wr.getBankName())
                .accountNumber(wr.getAccountNumber())
                .accountHolder(wr.getAccountHolder())
                .status(wr.getStatus())
                .adminNote(wr.getAdminNote())
                .processedByName(wr.getProcessedBy() != null ? wr.getProcessedBy().getFullName() : null)
                .processedAt(wr.getProcessedAt())
                .createdAt(wr.getCreatedAt())
                .studentName(wr.getStudent().getFullName())
                .studentMssv(wr.getStudent().getMssv())
                .studentEmail(wr.getStudent().getEmail())
                .build();
    }
}
