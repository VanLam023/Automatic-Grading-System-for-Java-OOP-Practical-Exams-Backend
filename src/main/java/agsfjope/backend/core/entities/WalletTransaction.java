package agsfjope.backend.core.entities;

import agsfjope.backend.core.enums.WalletTransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lịch sử giao dịch ví.
 * Ghi lại mỗi lần tiền vào/ra khỏi ví với số dư trước/sau.
 */
@Entity
@Table(name = "WalletTransactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "TransactionID")
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "WalletID", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "Type", nullable = false, columnDefinition = "wallet_transaction_type")
    private WalletTransactionType type;

    /** Số tiền giao dịch (luôn dương) */
    @Column(name = "Amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "BalanceBefore", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "BalanceAfter", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    /** AppealID hoặc WithdrawalID liên quan */
    @Column(name = "ReferenceID")
    private UUID referenceId;

    /** 'APPEAL', 'WITHDRAWAL', 'DEPOSIT' */
    @Column(name = "ReferenceType", length = 50)
    private String referenceType;

    @Column(name = "Description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
