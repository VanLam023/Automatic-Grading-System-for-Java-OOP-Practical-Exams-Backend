package agsfjope.backend.core.repositories.auth;

import agsfjope.backend.core.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing User entities in the database.
 * Extends JpaRepository to provide standard CRUD operations automatically via Spring Data JPA.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds a User by their unique username.
     * Used in the Login flow to look up a user before verifying their password.
     * @param username the username to search for
     * @return an Optional containing the User if found, or empty if not found
     */
    @EntityGraph(attributePaths = {"role"})
    Optional<User> findByUsername(String username);

    /**
     * Finds a User by their unique email address.
     * @param email the email to search for
     * @return an Optional containing the User if found, or empty if not found
     */
    Optional<User> findByEmail(String email);
    /**
     * Finds a user by their MSSV (student ID) — used during registration to check uniqueness.
     * @param mssv the student ID (e.g. se173173)
     * @return Optional containing the User if found
     */
    Optional<User> findByMssv(String mssv);

    /**
     * Finds all Users whose email is in the provided list.
     * Used for batch duplicate checking during Excel import to avoid N+1 queries.
     * @param emails list of email addresses to check (should be lowercase)
     * @return list of matching User entities
     */
    List<User> findByEmailIn(List<String> emails);

    /**
     * Finds all Users whose MSSV is in the provided list.
     * Used for batch duplicate checking during Excel import to avoid N+1 queries.
     * @param mssvs list of student IDs to check
     * @return list of matching User entities
     */
    List<User> findByMssvIn(List<String> mssvs);

    /**
     * Finds all Users whose username is in the provided list.
     * Used for batch duplicate checking during Excel import to avoid N+1 queries.
     * @param usernames list of usernames to check
     * @return list of matching User entities
     */
    List<User> findByUsernameIn(List<String> usernames);

    /**
     * Returns a paginated list of all non-deleted users, eagerly loading their Role.
     * Used by the Admin "Get All Users" endpoint.
     *
     * @param pageable pagination and sorting configuration
     * @return page of active (non-soft-deleted) users
     */
    @EntityGraph(attributePaths = {"role"})
    Page<User> findAllByDeletedAtIsNull(Pageable pageable);

    /**
     * Full-text search across username, email, and fullName with optional role filter.
     * Excludes soft-deleted users. All string comparisons are case-insensitive.
     *
     * @param keyword  search term (matched with LIKE against username, email, fullName); null to skip
     * @param roleName exact role name filter (e.g. "STUDENT"); null to skip
     * @param pageable pagination and sorting configuration
     * @return page of matching non-soft-deleted users
     */
    @EntityGraph(attributePaths = {"role"})
    @Query("""
            SELECT u FROM User u
            WHERE u.deletedAt IS NULL
              AND (:keyword IS NULL OR
                   LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(u.email)    LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:roleName IS NULL OR u.role.name = :roleName)
            """)
    Page<User> searchUsers(
            @Param("keyword")  String keyword,
            @Param("roleName") String roleName,
            Pageable pageable);
}
