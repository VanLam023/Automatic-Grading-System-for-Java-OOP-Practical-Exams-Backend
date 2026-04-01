package agsfjope.backend.core.enums;

public enum WithdrawalStatus {
    PENDING,    // Chờ admin xử lý
    APPROVED,   // Admin đã duyệt
    REJECTED,   // Admin từ chối
    COMPLETED   // Đã hoàn tất chuyển khoản
}
