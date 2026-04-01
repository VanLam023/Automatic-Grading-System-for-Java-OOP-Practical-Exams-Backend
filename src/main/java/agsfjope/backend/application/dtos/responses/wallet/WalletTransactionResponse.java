package agsfjope.backend.application.dtos.responses.wallet;

import agsfjope.backend.core.enums.WalletTransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Chi tiết một giao dịch ví.
 */
@Data
@Builder
public class WalletTransactionResponse {
    private UUID transactionId;
    private WalletTransactionType type;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String description;
    private UUID referenceId;
    private String referenceType;
    private OffsetDateTime createdAt;
}
