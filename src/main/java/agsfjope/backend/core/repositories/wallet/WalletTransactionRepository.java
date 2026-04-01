package agsfjope.backend.core.repositories.wallet;

import agsfjope.backend.core.entities.WalletTransaction;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface cho {@link WalletTransaction}.
 */
public interface WalletTransactionRepository {

    /**
     * Lưu giao dịch.
     */
    WalletTransaction save(WalletTransaction transaction);

    /**
     * Lấy toàn bộ lịch sử giao dịch của 1 ví, mới nhất trước.
     */
    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
}
