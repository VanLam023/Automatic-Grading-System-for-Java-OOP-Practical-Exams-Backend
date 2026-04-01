package agsfjope.backend.application.dtos.responses.wallet;

import agsfjope.backend.core.enums.WithdrawalStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Chi tiết một yêu cầu rút tiền.
 */
@Data
@Builder
public class WithdrawalResponse {
    private UUID withdrawalId;
    private BigDecimal amount;
    private String bankName;
    private String accountNumber;
    private String accountHolder;
    private WithdrawalStatus status;
    private String adminNote;
    private String processedByName;
    private OffsetDateTime processedAt;
    private OffsetDateTime createdAt;

    // Dành cho Admin: thêm thông tin student
    private String studentName;
    private String studentMssv;
    private String studentEmail;
}
