package agsfjope.backend.application.walletservices.impl;

import agsfjope.backend.application.dtos.requests.wallet.DepositRequest;
import agsfjope.backend.application.dtos.requests.wallet.ProcessWithdrawalRequest;
import agsfjope.backend.application.dtos.requests.wallet.WithdrawRequest;
import agsfjope.backend.application.dtos.responses.wallet.DepositResponse;
import agsfjope.backend.application.dtos.responses.wallet.WalletResponse;
import agsfjope.backend.application.dtos.responses.wallet.WithdrawalResponse;
import agsfjope.backend.application.notificationservices.NotificationService;
import agsfjope.backend.application.ports.out.PaymentGatewayPort;
import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.enums.WalletTransactionType;
import agsfjope.backend.core.enums.WithdrawalStatus;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.core.repositories.payment.PaymentRepository;
import agsfjope.backend.core.repositories.wallet.WalletRepository;
import agsfjope.backend.core.repositories.wallet.WalletTransactionRepository;
import agsfjope.backend.core.repositories.wallet.WithdrawalRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletTransactionRepository walletTransactionRepository;
    @Mock
    private WithdrawalRequestRepository withdrawalRequestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGatewayPort paymentGatewayPort;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SystemConfigRepository systemConfigRepository;

    @Mock private agsfjope.backend.infrastructure.audit.AuditLogHelper auditLogHelper;

    @InjectMocks
    private WalletServiceImpl walletService;

    // =========================================================================
    // 1. getMyWallet
    // =========================================================================
    @Test
    @DisplayName("[N] getMyWallet_WalletExist_ReturnsWalletResponse")
    void getMyWallet_WalletExist_ReturnsWalletResponse() {
        // Arrange
        UUID studentId = UUID.randomUUID();
        Wallet wallet = new Wallet();
        wallet.setWalletId(UUID.randomUUID());
        wallet.setBalance(new BigDecimal("500000"));

        WalletTransaction tx = new WalletTransaction();
        tx.setTransactionId(UUID.randomUUID());
        tx.setType(WalletTransactionType.DEPOSIT);
        tx.setAmount(new BigDecimal("500000"));
        tx.setBalanceBefore(BigDecimal.ZERO);
        tx.setBalanceAfter(new BigDecimal("500000"));

        when(paymentRepository.findPendingWalletDepositsByStudentId(studentId))
                .thenReturn(Collections.emptyList());
        when(walletRepository.findByStudentId(studentId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getWalletId()))
                .thenReturn(List.of(tx));

        // Act
        WalletResponse response = walletService.getMyWallet(studentId);

        // Assert
        assertNotNull(response);
        assertEquals(Boolean.TRUE, response.getHasWallet());
        assertEquals(wallet.getWalletId(), response.getWalletId());
        assertEquals(1, response.getTransactions().size());
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("[N] getMyWallet_WalletNotExist_ReturnsEmptyWalletView")
    void getMyWallet_WalletNotExist_ReturnsEmptyWalletView() {
        // Arrange
        UUID studentId = UUID.randomUUID();
        when(paymentRepository.findPendingWalletDepositsByStudentId(studentId))
                .thenReturn(Collections.emptyList());
        when(walletRepository.findByStudentId(studentId)).thenReturn(Optional.empty());

        // Act
        WalletResponse response = walletService.getMyWallet(studentId);

        // Assert
        assertNotNull(response);
        assertEquals(Boolean.FALSE, response.getHasWallet());
        assertEquals(BigDecimal.ZERO, response.getBalance());
        assertTrue(response.getTransactions().isEmpty());
        verify(walletRepository, never()).save(any(Wallet.class));
    }


    @Test
    @DisplayName("[N] getMyWallet_CoPendingWalletDepositPaid_StudentOpenWallet_AutoCreditsOnce")
    void getMyWallet_PendingWalletDepositPaid_AutoCreditsOnce() {
        UUID studentId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        Wallet wallet = new Wallet();
        wallet.setWalletId(walletId);
        wallet.setBalance(new BigDecimal("20000"));

        User student = new User();
        student.setUserId(studentId);

        Payment pendingDeposit = Payment.builder()
                .paymentId(paymentId)
                .student(student)
                .depositForStudent(student)
                .paymentPurpose("WALLET_DEPOSIT")
                .amount(new BigDecimal("10000"))
                .payosOrderId("123456")
                .status(agsfjope.backend.core.enums.PaymentStatus.PENDING)
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .build();

        when(paymentRepository.findPendingWalletDepositsByStudentId(studentId))
                .thenReturn(List.of(pendingDeposit));
        when(paymentGatewayPort.getPaymentLinkInfo("123456"))
                .thenReturn(new PaymentGatewayPort.PaymentLinkInfo(
                        "pl_123",
                        123456L,
                        10000L,
                        10000L,
                        0L,
                        "PAID",
                        "{\"status\":\"PAID\"}"
                ));
        when(paymentRepository.markSuccessIfPending(eq(paymentId), any(OffsetDateTime.class), anyString()))
                .thenReturn(1);
        when(walletRepository.findByStudentId(studentId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(walletId))
                .thenReturn(Collections.emptyList());
        when(walletRepository.adjustBalance(walletId, new BigDecimal("10000"))).thenReturn(1);

        WalletResponse response = walletService.getMyWallet(studentId);

        assertNotNull(response);
        verify(paymentRepository).findPendingWalletDepositsByStudentId(studentId);
        verify(paymentRepository).markSuccessIfPending(eq(paymentId), any(OffsetDateTime.class), anyString());
        verify(walletRepository).adjustBalance(walletId, new BigDecimal("10000"));
        verify(walletTransactionRepository).save(any(WalletTransaction.class));
    }

    @Test
    @DisplayName("[B] getMyWallet_CoPendingWalletDepositPaidButAlreadyProcessed_KhongCreditTrung")
    void getMyWallet_PendingWalletDepositPaidButAlreadyProcessed_DoesNotDoubleCredit() {
        UUID studentId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        Wallet wallet = new Wallet();
        wallet.setWalletId(walletId);
        wallet.setBalance(new BigDecimal("20000"));

        User student = new User();
        student.setUserId(studentId);

        Payment pendingDeposit = Payment.builder()
                .paymentId(paymentId)
                .student(student)
                .depositForStudent(student)
                .paymentPurpose("WALLET_DEPOSIT")
                .amount(new BigDecimal("10000"))
                .payosOrderId("123456")
                .status(agsfjope.backend.core.enums.PaymentStatus.PENDING)
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .build();

        when(paymentRepository.findPendingWalletDepositsByStudentId(studentId))
                .thenReturn(List.of(pendingDeposit));
        when(paymentGatewayPort.getPaymentLinkInfo("123456"))
                .thenReturn(new PaymentGatewayPort.PaymentLinkInfo(
                        "pl_123",
                        123456L,
                        10000L,
                        10000L,
                        0L,
                        "PAID",
                        "{\"status\":\"PAID\"}"
                ));
        when(paymentRepository.markSuccessIfPending(eq(paymentId), any(OffsetDateTime.class), anyString()))
                .thenReturn(0);
        when(walletRepository.findByStudentId(studentId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(walletId))
                .thenReturn(Collections.emptyList());

        WalletResponse response = walletService.getMyWallet(studentId);

        assertNotNull(response);
        verify(paymentRepository).markSuccessIfPending(eq(paymentId), any(OffsetDateTime.class), anyString());
        verify(walletRepository, never()).adjustBalance(any(), eq(new BigDecimal("10000")));
        verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
    }

    // =========================================================================
    // 2. deposit
    // =========================================================================
    @Test
    @DisplayName("[N] deposit_ValidRequest_Success")
    void deposit_ValidRequest_Success() throws Exception {
        // Arrange
        UUID studentId = UUID.randomUUID();
        User student = new User();
        student.setUserId(studentId);
        student.setMssv("SE12345");

        DepositRequest request = new DepositRequest();
        request.setAmount(new BigDecimal("100000"));
        request.setReturnUrl("http://localhost:5173/return");
        request.setCancelUrl("http://localhost:5173/cancel");

        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        
        SystemConfig config = new SystemConfig();
        config.setConfigValue("10");
        when(systemConfigRepository.findByConfigKey("APPEAL_PAYMENT_TIMEOUT_MINUTES"))
                .thenReturn(Optional.of(config));

        Payment savedPayment = new Payment();
        savedPayment.setPaymentId(UUID.randomUUID());
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        PaymentGatewayPort.PaymentLinkResult linkResult = new PaymentGatewayPort.PaymentLinkResult(
                "linkId123", "http://checkout", "http://qr", "PENDING"
        );
        when(paymentGatewayPort.createPaymentLink(anyLong(), eq(new BigDecimal("100000")), eq("SE12345"),
                eq("http://localhost:5173/return"), eq("http://localhost:5173/cancel"))).thenReturn(linkResult);

        // Act
        DepositResponse response = walletService.deposit(studentId, request);

        // Assert
        assertNotNull(response);
        assertEquals("http://checkout", response.getCheckoutUrl());
        verify(paymentRepository, times(2)).save(any(Payment.class));
    }

    @Test
    @DisplayName("[N] deposit_PayOSTimeout_ReturnsFallbackResponse")
    void deposit_PayOSTimeout_ReturnsFallbackResponse() throws Exception {
        // Arrange
        UUID studentId = UUID.randomUUID();
        User student = new User();
        student.setUserId(studentId);

        DepositRequest request = new DepositRequest();
        request.setAmount(new BigDecimal("100000"));
        request.setReturnUrl("http://localhost:5173/return");
        request.setCancelUrl("http://localhost:5173/cancel");

        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(systemConfigRepository.findByConfigKey(anyString())).thenReturn(Optional.empty()); // use default

        Payment savedPayment = new Payment();
        savedPayment.setPaymentId(UUID.randomUUID());
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        when(paymentGatewayPort.createPaymentLink(anyLong(), any(), anyString(), any(), any()))
                .thenThrow(new RuntimeException("PayOS Timeout"));

        // Act
        DepositResponse response = walletService.deposit(studentId, request);

        // Assert
        assertNotNull(response);
        assertNull(response.getCheckoutUrl());
        assertNotNull(response.getPayosError());
        verify(paymentRepository, times(1)).save(any(Payment.class)); // only saved before call
    }

    @Test
    @DisplayName("[A] deposit_StudentNotFound_ThrowsIllegalArgumentException")
    void deposit_StudentNotFound_ThrowsIllegalArgumentException() {
        // Arrange
        UUID studentId = UUID.randomUUID();
        when(userRepository.findById(studentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> walletService.deposit(studentId, new DepositRequest()));
    }

    // =========================================================================
    // 3. requestWithdrawal
    // =========================================================================
    @Test
    @DisplayName("[N] requestWithdrawal_ValidRequest_Success")
    void requestWithdrawal_ValidRequest_Success() {
        // Arrange
        UUID studentId = UUID.randomUUID();
        User student = new User();
        student.setUserId(studentId);

        WithdrawRequest request = new WithdrawRequest();
        request.setAmount(new BigDecimal("500000"));
        request.setBankName("VCB");

        Wallet wallet = new Wallet();
        wallet.setBalance(new BigDecimal("1000000"));

        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(walletRepository.findByStudentId(studentId)).thenReturn(Optional.of(wallet));

        WithdrawalRequest savedWr = new WithdrawalRequest();
        savedWr.setWithdrawalId(UUID.randomUUID());
        savedWr.setAmount(new BigDecimal("500000"));
        savedWr.setStatus(WithdrawalStatus.PENDING);
        savedWr.setStudent(student); // needed for toWithdrawalResponse
        when(withdrawalRequestRepository.save(any(WithdrawalRequest.class))).thenReturn(savedWr);

        // system admins
        User admin = new User();
        admin.setUserId(UUID.randomUUID());
        when(userRepository.findByRole_NameAndDeletedAtIsNull("SYSTEM_ADMIN")).thenReturn(List.of(admin));

        // Act
        WithdrawalResponse response = walletService.requestWithdrawal(studentId, request);

        // Assert
        assertNotNull(response);
        assertEquals(WithdrawalStatus.PENDING, response.getStatus());
        verify(notificationService).createNotification(eq(admin.getUserId()), anyString(), anyString(), eq("WITHDRAWAL"), eq(savedWr.getWithdrawalId()));
    }

    @Test
    @DisplayName("[A] requestWithdrawal_InsufficientBalance_ThrowsException")
    void requestWithdrawal_InsufficientBalance_ThrowsException() {
        // Arrange
        UUID studentId = UUID.randomUUID();
        User student = new User();
        WithdrawRequest request = new WithdrawRequest();
        request.setAmount(new BigDecimal("500000"));

        Wallet wallet = new Wallet();
        wallet.setBalance(new BigDecimal("100000")); // less than required

        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(walletRepository.findByStudentId(studentId)).thenReturn(Optional.of(wallet));

        // Act & Assert
        Exception e = assertThrows(IllegalStateException.class, () -> walletService.requestWithdrawal(studentId, request));
        assertTrue(e.getMessage().contains("Số dư có thể rút hiện tại không đủ"));
    }

    @Test
    @DisplayName("[B] requestWithdrawal_WalletNotFound_ThrowsException")
    void requestWithdrawal_WalletNotFound_ThrowsException() {
        // Arrange
        UUID studentId = UUID.randomUUID();
        User student = new User();
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(walletRepository.findByStudentId(studentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> walletService.requestWithdrawal(studentId, new WithdrawRequest()));
    }

    // =========================================================================
    // 4. getMyWithdrawals & 5. getAllWithdrawals
    // =========================================================================
    @Test
    @DisplayName("[N] getMyWithdrawals_ReturnsList")
    void getMyWithdrawals_ReturnsList() {
        // Arrange
        WithdrawalRequest wr = new WithdrawalRequest();
        User student = new User();
        wr.setStudent(student);
        when(withdrawalRequestRepository.findByStudentIdOrderByCreatedAtDesc(any(UUID.class)))
                .thenReturn(List.of(wr));

        // Act
        List<WithdrawalResponse> list = walletService.getMyWithdrawals(UUID.randomUUID());

        // Assert
        assertEquals(1, list.size());
    }

    @Test
    @DisplayName("[N] getAllWithdrawals_ValidFilter_ReturnsFiltered")
    void getAllWithdrawals_ValidFilter_ReturnsFiltered() {
        // Arrange
        WithdrawalRequest wr = new WithdrawalRequest();
        User student = new User();
        wr.setStudent(student);
        when(withdrawalRequestRepository.findByStatusOrderByCreatedAtDesc(WithdrawalStatus.PENDING))
                .thenReturn(List.of(wr));

        // Act
        List<WithdrawalResponse> list = walletService.getAllWithdrawals("PENDING");

        // Assert
        assertEquals(1, list.size());
    }

    @Test
    @DisplayName("[A] getAllWithdrawals_InvalidFilter_ThrowsException")
    void getAllWithdrawals_InvalidFilter_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> walletService.getAllWithdrawals("INVALID"));
    }

    // =========================================================================
    // 6. processWithdrawal
    // =========================================================================
    @Test
    @DisplayName("[N] processWithdrawal_Approve_Success")
    void processWithdrawal_Approve_Success() {
        // Arrange
        UUID wrId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        ProcessWithdrawalRequest request = new ProcessWithdrawalRequest();
        request.setIsApproved(true);
        request.setAdminNote("Ok");

        User student = new User();
        student.setUserId(UUID.randomUUID());
        
        Wallet wallet = new Wallet();
        wallet.setWalletId(UUID.randomUUID());
        wallet.setBalance(new BigDecimal("1000000"));

        WithdrawalRequest wr = new WithdrawalRequest();
        wr.setWithdrawalId(wrId);
        wr.setStatus(WithdrawalStatus.PENDING);
        wr.setAmount(new BigDecimal("500000"));
        wr.setStudent(student);
        wr.setWallet(wallet);

        User admin = new User();

        when(withdrawalRequestRepository.findById(wrId)).thenReturn(Optional.of(wr));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(walletRepository.adjustBalance(wallet.getWalletId(), new BigDecimal("-500000"))).thenReturn(1);

        // Act
        WithdrawalResponse res = walletService.processWithdrawal(wrId, request, adminId);

        // Assert
        assertEquals(WithdrawalStatus.APPROVED, res.getStatus());
        verify(walletTransactionRepository).save(any());
        verify(withdrawalRequestRepository).save(wr);
        verify(notificationService).createNotification(eq(student.getUserId()), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("[N] processWithdrawal_Reject_Success")
    void processWithdrawal_Reject_Success() {
        // Arrange
        UUID wrId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        ProcessWithdrawalRequest request = new ProcessWithdrawalRequest();
        request.setIsApproved(false);
        request.setAdminNote("No");

        User student = new User();
        student.setUserId(UUID.randomUUID());
        
        WithdrawalRequest wr = new WithdrawalRequest();
        wr.setWithdrawalId(wrId);
        wr.setStatus(WithdrawalStatus.PENDING);
        wr.setStudent(student);
        wr.setWallet(new Wallet());

        User admin = new User();

        when(withdrawalRequestRepository.findById(wrId)).thenReturn(Optional.of(wr));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        // Act
        WithdrawalResponse res = walletService.processWithdrawal(wrId, request, adminId);

        // Assert
        assertEquals(WithdrawalStatus.REJECTED, res.getStatus());
        assertEquals("No", res.getAdminNote());
        verify(walletRepository, never()).adjustBalance(any(), any());
        verify(walletTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("[B] processWithdrawal_AlreadyProcessed_ThrowsException")
    void processWithdrawal_AlreadyProcessed_ThrowsException() {
        // Arrange
        UUID wrId = UUID.randomUUID();
        WithdrawalRequest wr = new WithdrawalRequest();
        wr.setStatus(WithdrawalStatus.APPROVED);

        when(withdrawalRequestRepository.findById(wrId)).thenReturn(Optional.of(wr));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> walletService.processWithdrawal(wrId, new ProcessWithdrawalRequest(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("[A] processWithdrawal_Approve_NoRowsUpdated_ThrowsException")
    void processWithdrawal_Approve_NoRowsUpdated_ThrowsException() {
        // Arrange
        UUID wrId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        ProcessWithdrawalRequest request = new ProcessWithdrawalRequest();
        request.setIsApproved(true);

        User student = new User();
        Wallet wallet = new Wallet();
        wallet.setWalletId(UUID.randomUUID());
        wallet.setBalance(new BigDecimal("1000000"));

        WithdrawalRequest wr = new WithdrawalRequest();
        wr.setStatus(WithdrawalStatus.PENDING);
        wr.setAmount(new BigDecimal("500000"));
        wr.setStudent(student);
        wr.setWallet(wallet);

        when(withdrawalRequestRepository.findById(wrId)).thenReturn(Optional.of(wr));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(new User()));
        when(walletRepository.adjustBalance(wallet.getWalletId(), new BigDecimal("-500000"))).thenReturn(0);

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> walletService.processWithdrawal(wrId, request, adminId));
    }

    // =========================================================================
    // 7. creditWallet
    // =========================================================================
    @Test
    @DisplayName("[N] creditWallet_Success")
    void creditWallet_Success() {
        // Arrange
        UUID studentId = UUID.randomUUID();
        Wallet wallet = new Wallet();
        wallet.setWalletId(UUID.randomUUID());
        wallet.setBalance(new BigDecimal("1000"));

        when(walletRepository.findByStudentId(studentId)).thenReturn(Optional.of(wallet));

        // Act
        walletService.creditWallet(studentId, new BigDecimal("500"), UUID.randomUUID());

        // Assert
        verify(walletRepository).adjustBalance(wallet.getWalletId(), new BigDecimal("500"));
        verify(walletTransactionRepository).save(any(WalletTransaction.class));
    }

    // =========================================================================
    // 8. debitWalletForAppeal
    // =========================================================================
    @Test
    @DisplayName("[N] debitWalletForAppeal_Success")
    void debitWalletForAppeal_Success() {
        // Arrange
        UUID studentId = UUID.randomUUID();
        Wallet wallet = new Wallet();
        wallet.setWalletId(UUID.randomUUID());
        wallet.setBalance(new BigDecimal("500000"));

        when(walletRepository.findByStudentId(studentId)).thenReturn(Optional.of(wallet));
        when(walletRepository.adjustBalance(wallet.getWalletId(), new BigDecimal("-200000"))).thenReturn(1);

        // Act
        Wallet updated = walletService.debitWalletForAppeal(studentId, new BigDecimal("200000"), UUID.randomUUID());

        // Assert
        assertEquals(new BigDecimal("300000"), updated.getBalance());
        verify(walletTransactionRepository).save(any(WalletTransaction.class));
    }

    @Test
    @DisplayName("[B] debitWalletForAppeal_InsufficientBalance_ThrowsException")
    void debitWalletForAppeal_InsufficientBalance_ThrowsException() {
        // Arrange
        UUID studentId = UUID.randomUUID();
        Wallet wallet = new Wallet();
        wallet.setBalance(new BigDecimal("100000")); // less than 200k

        when(walletRepository.findByStudentId(studentId)).thenReturn(Optional.of(wallet));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> walletService.debitWalletForAppeal(studentId, new BigDecimal("200000"), UUID.randomUUID()));
    }

    // =========================================================================
    // 9. refundToWallet
    // =========================================================================
    @Test
    @DisplayName("[N] refundToWallet_Success")
    void refundToWallet_Success() {
        // Arrange
        UUID studentId = UUID.randomUUID();
        Wallet wallet = new Wallet();
        wallet.setWalletId(UUID.randomUUID());
        wallet.setBalance(new BigDecimal("1000"));

        when(walletRepository.findByStudentId(studentId)).thenReturn(Optional.of(wallet));

        // Act
        walletService.refundToWallet(studentId, new BigDecimal("200000"), UUID.randomUUID());

        // Assert
        verify(walletRepository).adjustBalance(wallet.getWalletId(), new BigDecimal("200000"));
        verify(walletTransactionRepository).save(any(WalletTransaction.class));
        verify(notificationService).createNotification(eq(studentId), anyString(), anyString(), eq("APPEAL"), any());
    }
}
