package agsfjope.backend.application.dtos.responses.wallet;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Thông tin ví của sinh viên (số dư + lịch sử giao dịch).
 */
@Data
@Builder
public class WalletResponse {
    private Boolean hasWallet;
    private UUID walletId;
    private BigDecimal balance;
    private BigDecimal appealFee;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<WalletTransactionResponse> transactions;
}
