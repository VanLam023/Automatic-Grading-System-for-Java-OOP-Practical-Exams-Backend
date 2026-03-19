package agsfjope.backend.core.repositories.auth;

import agsfjope.backend.core.entities.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
