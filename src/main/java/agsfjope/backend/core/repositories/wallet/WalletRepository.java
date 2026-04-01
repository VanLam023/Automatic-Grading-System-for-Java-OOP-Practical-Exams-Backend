package agsfjope.backend.core.repositories.wallet;

import agsfjope.backend.core.entities.Wallet;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface cho {@link Wallet}.
 * Tầng Core định nghĩa interface; Infrastructure implement bằng JPA.
 */
public interface WalletRepository {

    /**
     * Lưu hoặc cập nhật Wallet.
     */
    Wallet save(Wallet wallet);

    /**
     * Tìm ví theo StudentID.
     */
    Optional<Wallet> findByStudentId(UUID studentId);

    /**
     * Tìm ví theo WalletID.
     */
    Optional<Wallet> findByWalletId(UUID walletId);

    /**
     * Cập nhật số dư ví bằng JPQL UPDATE (optimistic, tránh race condition).
     * Chỉ cập nhật khi balance hiện tại >= delta (tránh âm ví).
     *
     * @param walletId UUID ví
     * @param delta    Số tiền thay đổi (dương = cộng, âm = trừ)
     * @return số bản ghi bị ảnh hưởng (0 = không thành công)
     */
    int adjustBalance(UUID walletId, BigDecimal delta);
}
