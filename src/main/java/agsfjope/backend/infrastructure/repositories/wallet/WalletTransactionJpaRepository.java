package agsfjope.backend.infrastructure.repositories.wallet;

import agsfjope.backend.core.entities.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA interface cho {@link WalletTransaction}.
 */
public interface WalletTransactionJpaRepository extends JpaRepository<WalletTransaction, UUID> {

    @Query("SELECT t FROM WalletTransaction t WHERE t.wallet.walletId = :walletId ORDER BY t.createdAt DESC")
    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(@Param("walletId") UUID walletId);
}
