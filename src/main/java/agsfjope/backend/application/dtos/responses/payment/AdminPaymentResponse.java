package agsfjope.backend.application.dtos.responses.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO chứa thông tin chi tiết của một giao dịch thanh toán PayOS dùng cho Admin quản lý.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPaymentResponse {
    private UUID paymentId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String payosOrderId;
    private String paymentPurpose;
    private OffsetDateTime expiresAt;
    private OffsetDateTime paidAt;
    private OffsetDateTime createdAt;

    // Thông tin sinh viên thực hiện giao dịch
    private UUID studentId;
    private String studentName;
    private String studentEmail;
    private String studentMssv;
}
