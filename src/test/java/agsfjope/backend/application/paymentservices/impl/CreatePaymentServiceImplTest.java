package agsfjope.backend.application.paymentservices.impl;

import agsfjope.backend.application.dtos.responses.payment.PaymentResponse;
import agsfjope.backend.application.ports.out.PaymentGatewayPort;
import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.entities.Payment;
import agsfjope.backend.core.entities.SystemConfig;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.PaymentStatus;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.core.repositories.payment.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit Tests cho CreatePaymentServiceImpl
 * Pattern: AAA (Arrange - Act - Assert)
 */
@ExtendWith(MockitoExtension.class)
class CreatePaymentServiceImplTest {

    @Mock
    private SystemConfigRepository systemConfigRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGatewayPort paymentGatewayPort;

    @InjectMocks
    private CreatePaymentServiceImpl createPaymentService;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    // =========================================================================
    // createPayment()
    // =========================================================================

    @Test
    @DisplayName("[N] createPayment - Lấy config hợp lệ -> Tạo link và lưu Payment thành công")
    void createPayment_ConfigLoadedAndValid_SavesPaymentAndReturnsResponse() {
        // Arrange
        Appeal mockAppeal = Appeal.builder().appealId(UUID.randomUUID()).build();
        User mockStudent = User.builder().userId(UUID.randomUUID()).build();
        String desc = "Pay for appeal";
        String returnUrl = "http://localhost/success";
        String cancelUrl = "http://localhost/cancel";

        SystemConfig feeConfig = SystemConfig.builder().configKey("APPEAL_FEE").configValue("100000").build();
        SystemConfig timeoutConfig = SystemConfig.builder().configKey("PAYMENT_TIMEOUT_MIN").configValue("20").build();
        when(systemConfigRepository.findByConfigKeyIn(anyList()))
                .thenReturn(List.of(feeConfig, timeoutConfig));

        PaymentGatewayPort.PaymentLinkResult mockLink = new PaymentGatewayPort.PaymentLinkResult(
                "linkId123", "http://payos.vn/checkout", "http://payos.vn/qr", "PENDING"
        );
        when(paymentGatewayPort.createPaymentLink(anyLong(), eq(new BigDecimal("100000")), 
                eq(desc), eq(returnUrl), eq(cancelUrl)))
                .thenReturn(mockLink);

        Payment mockSavedPayment = Payment.builder()
                .paymentId(UUID.randomUUID())
                .amount(new BigDecimal("100000"))
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .checkoutUrl("http://payos.vn/checkout")
                .qrCodeUrl("http://payos.vn/qr")
                .build();
        when(paymentRepository.save(any(Payment.class))).thenReturn(mockSavedPayment);

        // Act
        PaymentResponse response = createPaymentService.createPayment(
                mockAppeal, mockStudent, desc, returnUrl, cancelUrl);

        // Assert
        assertNotNull(response);
        assertEquals(mockSavedPayment.getPaymentId(), response.getPaymentId());
        assertEquals("http://payos.vn/checkout", response.getCheckoutUrl());
        assertEquals(PaymentStatus.PENDING.name(), response.getStatus());
        
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment capturedPayment = paymentCaptor.getValue();
        assertEquals(new BigDecimal("100000"), capturedPayment.getAmount());
        assertNotNull(capturedPayment.getExpiresAt());
    }

    @Test
    @DisplayName("[B] createPayment - Không có config trong DB -> Sử dụng giá trị Default (fee=200k, timeout=15m)")
    void createPayment_ConfigNotFound_UsesDefaultValuesAndSavesPayment() {
        // Arrange
        Appeal mockAppeal = Appeal.builder().appealId(UUID.randomUUID()).build();
        User mockStudent = User.builder().userId(UUID.randomUUID()).build();
        
        when(systemConfigRepository.findByConfigKeyIn(anyList())).thenReturn(Collections.emptyList());

        PaymentGatewayPort.PaymentLinkResult mockLink = new PaymentGatewayPort.PaymentLinkResult(
                "linkId123", "http://checkout", "http://qr", "PENDING"
        );
        when(paymentGatewayPort.createPaymentLink(anyLong(), eq(new BigDecimal("200000")), 
                anyString(), anyString(), anyString()))
                .thenReturn(mockLink);

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setPaymentId(UUID.randomUUID());
            return p;
        });

        // Act
        createPaymentService.createPayment(mockAppeal, mockStudent, "desc", "re", "ca");

        // Assert
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment capturedPayment = paymentCaptor.getValue();
        assertEquals(new BigDecimal("200000"), capturedPayment.getAmount(), "Phải dùng fee mặc định là 200.000");
    }

    @Test
    @DisplayName("[B] createPayment - Config chứa chữ hoặc format sai -> Catch Exception và dùng Default")
    void createPayment_ConfigInvalidFormat_CatchExceptionUsesDefault() {
        // Arrange
        Appeal mockAppeal = Appeal.builder().appealId(UUID.randomUUID()).build();
        User mockStudent = User.builder().userId(UUID.randomUUID()).build();
        
        SystemConfig invalidFeeConfig = SystemConfig.builder().configKey("APPEAL_FEE").configValue("invalid_fee").build();
        SystemConfig invalidTimeoutConfig = SystemConfig.builder().configKey("PAYMENT_TIMEOUT_MIN").configValue("abc").build();
        
        when(systemConfigRepository.findByConfigKeyIn(anyList()))
                .thenReturn(List.of(invalidFeeConfig, invalidTimeoutConfig));

        PaymentGatewayPort.PaymentLinkResult mockLink = new PaymentGatewayPort.PaymentLinkResult(
                "linkId123", "http://checkout", "http://qr", "PENDING"
        );
        when(paymentGatewayPort.createPaymentLink(anyLong(), eq(new BigDecimal("200000")), 
                anyString(), anyString(), anyString()))
                .thenReturn(mockLink);

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setPaymentId(UUID.randomUUID());
            return p;
        });

        // Act
        createPaymentService.createPayment(mockAppeal, mockStudent, "desc", "re", "ca");

        // Assert
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment capturedPayment = paymentCaptor.getValue();
        assertEquals(new BigDecimal("200000"), capturedPayment.getAmount());
    }

    @Test
    @DisplayName("[A] createPayment - PayOS Gateway bắn lỗi (RuntimeException) -> Throw Exception, không gọi repository.save")
    void createPayment_GatewayThrowsError_ThrowsExceptionAndSkipsSave() {
        // Arrange
        Appeal mockAppeal = Appeal.builder().appealId(UUID.randomUUID()).build();
        User mockStudent = User.builder().userId(UUID.randomUUID()).build();
        when(systemConfigRepository.findByConfigKeyIn(anyList())).thenReturn(Collections.emptyList());

        when(paymentGatewayPort.createPaymentLink(anyLong(), any(BigDecimal.class), 
                anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("PayOS Connection Error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            createPaymentService.createPayment(mockAppeal, mockStudent, "desc", "re", "ca");
        });
        assertEquals("PayOS Connection Error", exception.getMessage());
        
        // Ensure that repository save is NEVER called so it triggers Rollback later
        verify(paymentRepository, never()).save(any(Payment.class));
    }
}
