package agsfjope.backend.core.repositories.config;

import agsfjope.backend.core.entities.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link SystemConfig} entity.
 * Provides query methods for fetching individual config keys and grouped config keys.
 */
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Integer> {

    /**
     * Find a single configuration entry by exact config key.
     *
     * @param configKey unique system configuration key
     * @return optional configuration record
     */
    Optional<SystemConfig> findByConfigKey(String configKey);

    /**
     * Find a list of configuration entries where key is in provided key collection.
     *
     * @param keys list of configuration keys
     * @return list of matching configuration records
     */
    List<SystemConfig> findByConfigKeyIn(List<String> keys);
}
