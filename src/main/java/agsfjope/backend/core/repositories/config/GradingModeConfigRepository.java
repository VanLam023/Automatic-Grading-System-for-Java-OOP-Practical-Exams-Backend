package agsfjope.backend.core.repositories.config;

import agsfjope.backend.core.entities.GradingModeConfig;
import agsfjope.backend.core.enums.GradingMode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link GradingModeConfig} entity.
 * Supports querying by mode and listing all configured grading modes.
 */
public interface GradingModeConfigRepository extends JpaRepository<GradingModeConfig, Integer> {

    /**
     * Find grading mode configuration by enum mode.
     *
     * @param mode grading mode enum value
     * @return optional grading mode config
     */
    Optional<GradingModeConfig> findByMode(GradingMode mode);

    /**
     * Find all grading mode configurations sorted by mode ascending.
     *
     * @return sorted grading mode config list
     */
    List<GradingModeConfig> findAllByOrderByModeAsc();
}
