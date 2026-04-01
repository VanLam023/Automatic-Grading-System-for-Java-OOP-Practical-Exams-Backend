package agsfjope.backend.infrastructure.repositories.wallet;

import agsfjope.backend.core.entities.WalletTransaction;
import agsfjope.backend.core.repositories.wallet.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Adapter tầng Infrastructure implement {@link WalletTransactionRepository}.
 */
@Repository
@RequiredArgsConstructor
public class WalletTransactionRepositoryImpl implements WalletTransactionRepository {

    private final WalletTransactionJpaRepository jpaRepository;

    @Override
    public WalletTransaction save(WalletTransaction transaction) {
        return jpaRepository.save(transaction);
    }

    @Override
    public List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId) {
        return jpaRepository.findByWalletIdOrderByCreatedAtDesc(walletId);
    }
}
