package agsfjope.backend.infrastructure.repositories.wallet;

import agsfjope.backend.core.entities.WithdrawalRequest;
import agsfjope.backend.core.enums.WithdrawalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA interface cho {@link WithdrawalRequest}.
 */
public interface WithdrawalRequestJpaRepository extends JpaRepository<WithdrawalRequest, UUID> {

    @Query("SELECT w FROM WithdrawalRequest w WHERE w.student.userId = :studentId ORDER BY w.createdAt DESC")
    List<WithdrawalRequest> findByStudentIdOrderByCreatedAtDesc(@Param("studentId") UUID studentId);

    @Query("SELECT w FROM WithdrawalRequest w WHERE :status IS NULL OR w.status = :status ORDER BY w.createdAt DESC")
    List<WithdrawalRequest> findAllByStatus(@Param("status") WithdrawalStatus status);
}
