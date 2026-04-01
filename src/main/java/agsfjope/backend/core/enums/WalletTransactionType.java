package agsfjope.backend.core.enums;

public enum WalletTransactionType {
    DEPOSIT,        // Nạp tiền vào ví qua PayOS
    APPEAL_PAYMENT, // Trừ tiền ví để tạo phúc khảo
    APPEAL_REFUND,  // Hoàn tiền khi appeal APPROVED
    WITHDRAWAL      // Rút tiền ra ngoài
}
