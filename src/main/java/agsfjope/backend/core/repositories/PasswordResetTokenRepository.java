package agsfjope.backend.core.repositories;

import agsfjope.backend.core.entities.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing PasswordResetToken entities.
 * Maps to the PasswordResetTokens table defined in schema.sql.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /**
     * Finds a PasswordResetToken by its raw token string.
     * Used in the Forgot Password flow (verify + reset) to look up the token from the URL query param.
     *
     * @param tokenHash the raw UUID token string stored in the TokenHash column
     * @return an Optional containing the token entity if found, or empty if not found
     */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
