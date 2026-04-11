package agsfjope.backend.core.repositories.wallet;

import agsfjope.backend.core.entities.WithdrawalRequest;
import agsfjope.backend.core.enums.WithdrawalStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface cho {@link WithdrawalRequest}.
 */
public interface WithdrawalRequestRepository {

    /**
     * Lưu hoặc cập nhật yêu cầu rút tiền.
     */
    WithdrawalRequest save(WithdrawalRequest request);

    /**
     * Tìm theo ID.
     */
    Optional<WithdrawalRequest> findById(UUID withdrawalId);

    /**
     * Lấy danh sách yêu cầu rút tiền của 1 sinh viên, mới nhất trước.
     */
    List<WithdrawalRequest> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    /**
     * Tổng số tiền rút đang ở trạng thái PENDING của một sinh viên.
     * Dùng để chặn gửi nhiều yêu cầu vượt quá số dư có thể rút.
     */
    java.math.BigDecimal sumPendingAmountByStudentId(UUID studentId);

    /**
     * Lấy toàn bộ yêu cầu rút tiền, mới nhất trước.
     */
    List<WithdrawalRequest> findAllOrderByCreatedAtDesc();

    /**
     * Lấy toàn bộ yêu cầu rút tiền theo status, mới nhất trước.
     */
    List<WithdrawalRequest> findByStatusOrderByCreatedAtDesc(WithdrawalStatus status);
}
