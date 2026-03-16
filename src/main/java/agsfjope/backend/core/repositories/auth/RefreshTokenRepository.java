package agsfjope.backend.core.repositories.auth;

import agsfjope.backend.core.entities.RefreshToken;
import agsfjope.backend.core.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing RefreshToken entities in the database.
 * Extends JpaRepository to provide standard CRUD operations.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Finds a RefreshToken by its token hash string.
     * Used in the Refresh Token flow (SD_01_2) to look up an incoming token from the client.
     * @param tokenHash the raw token string sent by the client
     * @return an Optional containing the token entity if found
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes (marks as IsRevoked = true) all active refresh tokens belonging to a specific user.
     * This is called at the start of every new login to invalidate old sessions before issuing new tokens.
     * Using a JPQL UPDATE query for efficiency instead of loading all entities into memory.
     * @param user the User entity whose tokens should be revoked
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.user = :user AND rt.isRevoked = false")
    void revokeAllByUser(User user);
}
