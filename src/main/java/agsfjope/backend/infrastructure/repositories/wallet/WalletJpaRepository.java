package agsfjope.backend.infrastructure.repositories.wallet;

import agsfjope.backend.core.entities.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA interface cho {@link Wallet}.
 */
public interface WalletJpaRepository extends JpaRepository<Wallet, UUID> {

    @Query("SELECT w FROM Wallet w WHERE w.student.userId = :studentId")
    Optional<Wallet> findByStudentId(@Param("studentId") UUID studentId);

    /**
     * Điều chỉnh số dư ví theo delta (+ hoặc -).
     * Khi trừ tiền (delta âm): chỉ UPDATE khi balance + delta >= 0 để tránh âm ví.
     * Khi cộng tiền (delta dương): luôn UPDATE.
     *
     * @return số bản ghi bị ảnh hưởng (0 = thất bại vì số dư không đủ)
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE Wallet w SET w.balance = w.balance + :delta, w.updatedAt = CURRENT_TIMESTAMP
            WHERE w.walletId = :walletId
              AND (w.balance + :delta >= 0)
            """)
    int adjustBalance(@Param("walletId") UUID walletId, @Param("delta") BigDecimal delta);
}
