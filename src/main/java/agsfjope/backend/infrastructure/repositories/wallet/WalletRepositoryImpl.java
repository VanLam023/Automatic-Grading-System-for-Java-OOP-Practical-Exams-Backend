package agsfjope.backend.infrastructure.repositories.wallet;

import agsfjope.backend.core.entities.Wallet;
import agsfjope.backend.core.repositories.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter tầng Infrastructure implement {@link WalletRepository}.
 */
@Repository
@RequiredArgsConstructor
public class WalletRepositoryImpl implements WalletRepository {

    private final WalletJpaRepository walletJpaRepository;

    @Override
    public Wallet save(Wallet wallet) {
        return walletJpaRepository.save(wallet);
    }

    @Override
    public Optional<Wallet> findByStudentId(UUID studentId) {
        return walletJpaRepository.findByStudentId(studentId);
    }

    @Override
    public Optional<Wallet> findByWalletId(UUID walletId) {
        return walletJpaRepository.findById(walletId);
    }

    @Override
    public int adjustBalance(UUID walletId, BigDecimal delta) {
        return walletJpaRepository.adjustBalance(walletId, delta);
    }
}
