package agsfjope.backend.application.paymentservices.impl;

import agsfjope.backend.application.dtos.requests.payment.PayOSWebhookRequest;
import agsfjope.backend.application.dtos.responses.payment.PaymentResponse;
import agsfjope.backend.application.ports.out.PaymentGatewayPort;
import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.entities.Payment;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.enums.PaymentStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.payment.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit Tests cho HandlePaymentServiceImpl
 * Pattern: AAA (Arrange - Act - Assert)
 */
@ExtendWith(MockitoExtension.class)
class HandlePaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGatewayPort paymentGatewayPort;

    @Mock
    private PaymentWebhookProcessor webhookProcessor;

    @Mock
    private AppealRepository appealRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private HandlePaymentServiceImpl handlePaymentService;

    // =========================================================================
    // handleWebhook(String rawBody)
    // =========================================================================

    @Test
    @DisplayName("[N] handleWebhook(String) - Dữ liệu hợp lệ, Checksum OK -> Delegate sang webhookProcessor")
    void handleWebhook_ValidChecksum_NotTestWebhook_DelegatesToProcessor() throws Exception {
        // Arrange
        String rawBody = "{\"code\":\"00\"}";
        PayOSWebhookRequest.WebhookData data = new PayOSWebhookRequest.WebhookData();
        data.setOrderCode(123456L); // > 0 -> not test webhook
        PayOSWebhookRequest requestDto = new PayOSWebhookRequest();
        requestDto.setCode("00");
        requestDto.setDesc("desc");
        requestDto.setData(data);
        requestDto.setSignature("signature");

        when(paymentGatewayPort.verifyWebhookChecksum(rawBody)).thenReturn(true);
        when(objectMapper.readValue(rawBody, PayOSWebhookRequest.class)).thenReturn(requestDto);

        // Act
        handlePaymentService.handleWebhook(rawBody);

        // Assert
        verify(webhookProcessor).process(requestDto);
    }

    @Test
    @DisplayName("[B] handleWebhook(String) - Checksum failed -> Bỏ qua, return early")
    void handleWebhook_InvalidChecksum_SkipsProcessing() {
        // Arrange
        String rawBody = "{\"code\":\"00\"}";
        when(paymentGatewayPort.verifyWebhookChecksum(rawBody)).thenReturn(false);

        // Act
        handlePaymentService.handleWebhook(rawBody);

        // Assert
        verifyNoInteractions(objectMapper);
        verifyNoInteractions(webhookProcessor);
    }

    @Test
    @DisplayName("[B] handleWebhook(String) - Parsing lỗi (JSON hỏng) -> Catch exception, skip")
    void handleWebhook_InvalidJson_CatchesException_SkipsProcessing() throws Exception {
        // Arrange
        String rawBody = "invalid json";
        when(paymentGatewayPort.verifyWebhookChecksum(rawBody)).thenReturn(true);
        when(objectMapper.readValue(rawBody, PayOSWebhookRequest.class))
                .thenThrow(new JsonProcessingException("parse error") {});

        // Act
        handlePaymentService.handleWebhook(rawBody);

        // Assert
        verifyNoInteractions(webhookProcessor);
    }

    @Test
    @DisplayName("[B] handleWebhook(String) - Là test webhook (orderCode=0) -> Bỏ qua, không gọi processor")
    void handleWebhook_TestWebhook_ReturnsEarly() throws Exception {
        // Arrange
        String rawBody = "{\"code\":\"00\"}";
        PayOSWebhookRequest.WebhookData data = new PayOSWebhookRequest.WebhookData();
        data.setOrderCode(0L); // test webhook
        PayOSWebhookRequest requestDto = new PayOSWebhookRequest();
        requestDto.setCode("00");
        requestDto.setDesc("desc");
        requestDto.setData(data);
        requestDto.setSignature("signature");

        when(paymentGatewayPort.verifyWebhookChecksum(rawBody)).thenReturn(true);
        when(objectMapper.readValue(rawBody, PayOSWebhookRequest.class)).thenReturn(requestDto);

        // Act
        handlePaymentService.handleWebhook(rawBody);

        // Assert
        verifyNoInteractions(webhookProcessor);
    }

    // =========================================================================
    // handleWebhook(PayOSWebhookRequest request)
    // =========================================================================

    @Test
    @DisplayName("[N] handleWebhook(DTO) - Chuyển sang chuỗi JSON và gọi hàm xử lý body string")
    void handleWebhook_DtoVersion_SerializesAndDelegates() throws Exception {
        // Arrange
        PayOSWebhookRequest request = new PayOSWebhookRequest();
        String jsonEncoded = "{\"code\":\"00\"}";
        when(objectMapper.writeValueAsString(request)).thenReturn(jsonEncoded);
        
        // Cần stub phần xử lý bên trong (checksum false để chạy lệnh return early cho nhanh)
        when(paymentGatewayPort.verifyWebhookChecksum(jsonEncoded)).thenReturn(false);

        // Act
        handlePaymentService.handleWebhook(request);

        // Assert
        verify(paymentGatewayPort).verifyWebhookChecksum(jsonEncoded);
    }

    @Test
    @DisplayName("[A] handleWebhook(DTO) - Serialization throw Exception -> Bỏ qua an toàn")
    void handleWebhook_DtoVersion_SerializationFails_CatchesException() throws Exception {
        // Arrange
        PayOSWebhookRequest request = new PayOSWebhookRequest();
        when(objectMapper.writeValueAsString(request)).thenThrow(new JsonProcessingException("serialize err") {});

        // Act
        handlePaymentService.handleWebhook(request);

        // Assert
        verifyNoInteractions(paymentGatewayPort);
    }

    // =========================================================================
    // retryPayment()
    // =========================================================================

    @Test
    @DisplayName("[N] retryPayment - Tồn tại PENDING, chưa expired -> Hủy link cũ, tạo link mới")
    void retryPayment_ValidPendingNotExpired_CancelsOldLinkCreatesNewLink() {
        // Arrange
        UUID appealId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .paymentId(UUID.randomUUID())
                .status(PaymentStatus.PENDING)
                .payosOrderId("100200")
                .payosPaymentLinkId("oldLinkId")
                .expiresAt(OffsetDateTime.now().plusMinutes(10)) // chưa hết hạn
                .build();
        
        when(paymentRepository.findByAppealId(appealId)).thenReturn(Optional.of(payment));

        PaymentGatewayPort.PaymentLinkResult mockResult = new PaymentGatewayPort.PaymentLinkResult(
                "newLinkId", "http://new.checkout", "http://new.qr", "PENDING"
        );
        when(paymentGatewayPort.createPaymentLink(eq(100200L), any(), anyString(), anyString(), anyString()))
                .thenReturn(mockResult);

        // Act
        PaymentResponse response = handlePaymentService.retryPayment(appealId);

        // Assert
        assertNotNull(response);
        assertEquals("http://new.checkout", response.getCheckoutUrl());
        
        verify(paymentGatewayPort).cancelPaymentLink("oldLinkId");
        verify(paymentGatewayPort).createPaymentLink(anyLong(), any(), anyString(), anyString(), anyString());
        verify(paymentRepository).save(payment);
        assertEquals("newLinkId", payment.getPayosPaymentLinkId());
    }

    @Test
    @DisplayName("[A] retryPayment - Không tìm thấy appeal -> Throw Exception")
    void retryPayment_NotFound_ThrowsException() {
        // Arrange
        UUID appealId = UUID.randomUUID();
        when(paymentRepository.findByAppealId(appealId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            handlePaymentService.retryPayment(appealId);
        });
        assertTrue(ex.getMessage().contains("No payment found"));
    }

    @Test
    @DisplayName("[B] retryPayment - Trạng thái đã SUCCESS -> Throw IllegalStateException")
    void retryPayment_WrongStatus_ThrowsIllegalStateException() {
        // Arrange
        UUID appealId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .status(PaymentStatus.SUCCESS)
                .build();
        when(paymentRepository.findByAppealId(appealId)).thenReturn(Optional.of(payment));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> handlePaymentService.retryPayment(appealId));
    }

    @Test
    @DisplayName("[B] retryPayment - Đã hết hạn -> Throw IllegalStateException")
    void retryPayment_AlreadyExpired_ThrowsIllegalStateException() {
        // Arrange
        UUID appealId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .status(PaymentStatus.PENDING)
                .expiresAt(OffsetDateTime.now().minusMinutes(5)) // Quá hạn 5 phút
                .build();
        when(paymentRepository.findByAppealId(appealId)).thenReturn(Optional.of(payment));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> handlePaymentService.retryPayment(appealId));
    }

    // =========================================================================
    // handleExpiredPayments()
    // =========================================================================

    @Test
    @DisplayName("[N] handleExpiredPayments - Có expired payment -> Hủy link PayOS ngầm, update Failed, cancel Appeal")
    void handleExpiredPayments_HasExpiredList_CancelsAndUpdatesStatus() {
        // Arrange
        Appeal appeal = Appeal.builder().appealId(UUID.randomUUID()).build();
        Payment p1 = Payment.builder()
                .paymentId(UUID.randomUUID())
                .payosPaymentLinkId("linkToCancel")
                .appeal(appeal)
                .build();
        
        Payment p2 = Payment.builder()
                .paymentId(UUID.randomUUID())
                .payosPaymentLinkId(null) // Không có link cũ, không cần cancel
                .appeal(null) // Cố tình null appeal check safe update
                .build();

        when(paymentRepository.findExpiredPendingPayments(any(OffsetDateTime.class)))
                .thenReturn(List.of(p1, p2));

        // Act
        handlePaymentService.handleExpiredPayments();

        // Assert
        verify(paymentGatewayPort).cancelPaymentLink("linkToCancel");
        verify(paymentGatewayPort, times(1)).cancelPaymentLink(anyString()); // Chỉ đúng 1 lần
        
        verify(paymentRepository).updateStatus(p1.getPaymentId(), PaymentStatus.FAILED);
        verify(paymentRepository).updateStatus(p2.getPaymentId(), PaymentStatus.FAILED);
        
        verify(appealRepository).updateStatus(appeal.getAppealId(), AppealStatus.CANCELLED);
    }

    @Test
    @DisplayName("[A] handleExpiredPayments - Quá trình xử lý bị throw Runtime -> Catch an toàn, tiếp tục cancel các phần tử sau")
    void handleExpiredPayments_ProcessingException_ContinuesNext() {
        // Arrange
        Payment p1 = Payment.builder().paymentId(UUID.randomUUID()).payosPaymentLinkId("buggyLink").build();
        Payment p2 = Payment.builder().paymentId(UUID.randomUUID()).payosPaymentLinkId(null).build();
        
        when(paymentRepository.findExpiredPendingPayments(any(OffsetDateTime.class)))
                .thenReturn(List.of(p1, p2));
        
        // Simulating error on first item
        doThrow(new RuntimeException("Network Error")).when(paymentGatewayPort).cancelPaymentLink("buggyLink");

        // Act
        handlePaymentService.handleExpiredPayments();

        // Assert
        // P1 throws error -> updateStatus for P1 is SKIPPED
        verify(paymentRepository, never()).updateStatus(p1.getPaymentId(), PaymentStatus.FAILED);
        
        // But loop continues -> P2 finishes properly without error
        verify(paymentRepository).updateStatus(p2.getPaymentId(), PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("[B] handleExpiredPayments - Không có item nào expired -> Không gọi logic")
    void handleExpiredPayments_EmptyList_ReturnsImmediately() {
        // Arrange
        when(paymentRepository.findExpiredPendingPayments(any(OffsetDateTime.class)))
                .thenReturn(Collections.emptyList());

        // Act
        handlePaymentService.handleExpiredPayments();

        // Assert
        verifyNoInteractions(paymentGatewayPort);
        verifyNoInteractions(appealRepository);
    }
}
