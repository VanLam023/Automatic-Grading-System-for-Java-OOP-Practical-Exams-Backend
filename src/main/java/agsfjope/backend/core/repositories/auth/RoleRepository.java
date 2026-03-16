package agsfjope.backend.core.repositories.auth;

import agsfjope.backend.core.entities.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Role entity.
 * Used by DataSeeder to look up roles by name during application startup.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {

    /**
     * Finds a role by its unique name (e.g., "SYSTEM_ADMIN", "STUDENT").
     * @param name the role name to search for
     * @return Optional containing the Role if found
     */
    Optional<Role> findByName(String name);
}
