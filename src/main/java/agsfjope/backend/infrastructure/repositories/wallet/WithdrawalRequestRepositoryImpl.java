package agsfjope.backend.infrastructure.repositories.wallet;

import agsfjope.backend.core.entities.WithdrawalRequest;
import agsfjope.backend.core.enums.WithdrawalStatus;
import agsfjope.backend.core.repositories.wallet.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter tầng Infrastructure implement {@link WithdrawalRequestRepository}.
 */
@Repository
@RequiredArgsConstructor
public class WithdrawalRequestRepositoryImpl implements WithdrawalRequestRepository {

    private final WithdrawalRequestJpaRepository jpaRepository;

    @Override
    public WithdrawalRequest save(WithdrawalRequest request) {
        return jpaRepository.save(request);
    }

    @Override
    public Optional<WithdrawalRequest> findById(UUID withdrawalId) {
        return jpaRepository.findById(withdrawalId);
    }

    @Override
    public List<WithdrawalRequest> findByStudentIdOrderByCreatedAtDesc(UUID studentId) {
        return jpaRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    }

    @Override
    public List<WithdrawalRequest> findAllByStatus(WithdrawalStatus status) {
        return jpaRepository.findAllByStatus(status);
    }
}
