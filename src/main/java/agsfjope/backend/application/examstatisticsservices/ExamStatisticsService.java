package agsfjope.backend.application.examstatisticsservices;

import agsfjope.backend.application.dtos.responses.statistics.BlockStatisticsResponse;

import java.util.UUID;

/**
 * Service interface for generating exam statistics per block (PROC-006).
 *
 * <p>Calculates and returns aggregated statistics for a specific block
 * within an exam, including submission overview, score analysis, AI OOP
 * analysis, and appeal/financial metrics.</p>
 */
public interface ExamStatisticsService {

    /**
     * Generates comprehensive statistics for a specific block.
     *
     * <p>Validates that the block belongs to the given exam (prevents
     * cross-exam data leakage), then aggregates data from GradingResult,
     * AIReview, Appeal, and WalletTransaction entities.</p>
     *
     * @param examId  the exam UUID (used for ownership validation)
     * @param blockId the block UUID to generate statistics for
     * @return aggregated statistics for the block
     */
    BlockStatisticsResponse getBlockStatistics(UUID examId, UUID blockId);
}
