package agsfjope.backend.core.repositories.auth;

import agsfjope.backend.core.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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
     * Returns a paginated list of all non-deleted, non-SYSTEM_ADMIN users,
     * eagerly loading their Role.
     * Used by the Admin "Get All Users" endpoint.
     *
     * @param pageable pagination and sorting configuration
     * @return page of active (non-soft-deleted) users excluding SYSTEM_ADMIN
     */
    @EntityGraph(attributePaths = {"role"})
    Page<User> findAllByDeletedAtIsNullAndRoleNameNot(String roleName, Pageable pageable);

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
              AND u.role.name <> 'SYSTEM_ADMIN'
              AND (:keyword = '' OR
                   LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(u.email)    LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(u.mssv)     LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:roleName = '' OR u.role.name = :roleName)
            """)
    Page<User> searchUsers(
            @Param("keyword")  String keyword,
            @Param("roleName") String roleName,
            Pageable pageable);

    /**
     * Counts all non-deleted users.
     * Used by the Admin Dashboard to display the total user count.
     *
     * @return total number of active (non-soft-deleted) users
     */
    long countByDeletedAtIsNull();

    /**
     * Counts non-deleted users that belong to a specific role.
     * Used by the Admin Dashboard to build the user-by-role donut chart.
     *
     * @param roleName the role name to filter by (e.g. "STUDENT", "LECTURER")
     * @return number of non-soft-deleted users with the given role
     */
    long countByRole_NameAndDeletedAtIsNull(String roleName);

    /**
     * Counts non-deleted users created within the given date range.
     * Used by the Admin Dashboard date-filtered overview.
     *
     * @param from start of date range (inclusive)
     * @param to   end of date range (inclusive)
     * @return number of non-soft-deleted users created between from and to
     */
    long countByDeletedAtIsNullAndCreatedAtBetween(OffsetDateTime from, OffsetDateTime to);

    /**
     * Counts non-deleted users of a specific role created within the given date range.
     * Used by the Admin Dashboard date-filtered user-by-role donut chart.
     *
     * @param roleName role name to filter by
     * @param from     start of date range (inclusive)
     * @param to       end of date range (inclusive)
     * @return number of matching non-soft-deleted users
     */
    long countByRole_NameAndDeletedAtIsNullAndCreatedAtBetween(String roleName, OffsetDateTime from, OffsetDateTime to);

    /**
     * Finds all active (non-soft-deleted) users with the specified role name.
     * Used to populate the lecturer dropdown on the Assign Appeal screen.
     *
     * @param roleName role name (e.g. "LECTURER")
     * @return list of matching active users
     */
    List<User> findByRole_NameAndDeletedAtIsNull(String roleName);
}
