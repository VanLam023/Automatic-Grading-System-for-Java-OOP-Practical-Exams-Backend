package agsfjope.backend.application.dtos.responses.statistics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for block-level statistics (PROC-006: Generate Exam Statistics).
 *
 * <p>Contains 4 groups of aggregated data for a specific block:
 * submission overview, score analysis, AI OOP analysis, and appeal/financial analysis.</p>
 *
 * <p>All percentage values are expressed as 0..100 (e.g., 75.5 means 75.5%).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockStatisticsResponse {

    // ─── (1) Submission Overview ─────────────────────────────────────────────

    /** Total number of submissions (any status) in this block. */
    private long totalSubmissions;

    /** Number of submissions that have been graded. */
    private long gradedSubmissions;

    // ─── (2) Score Analysis ─────────────────────────────────────────────────

    /** Score analysis metrics for the block. */
    private ScoreAnalysis scoreAnalysis;

    // ─── (3) AI OOP Analysis ────────────────────────────────────────────────

    /** AI-powered OOP quality analysis metrics. */
    private AiOopAnalysis aiOopAnalysis;

    // ─── (4) Appeal & Financial ───────────────────────────────────────────────

    /** Appeal and financial metrics for the block. */
    private AppealFinancialAnalysis appealFinancial;

    // ─── Nested DTOs ────────────────────────────────────────────────────────

    /**
     * Score-related statistics for the block.
     * Includes average/max/min scores, pass/fail rates, and score distribution histogram.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreAnalysis {
        /** Average total score across all graded submissions. */
        private BigDecimal avgScore;
        /** Highest total score in the block. */
        private BigDecimal maxScore;
        /** Lowest total score in the block. */
        private BigDecimal minScore;
        /** Number of PASS results (totalScore >= 4.0). */
        private long passCount;
        /** Number of FAIL results (totalScore < 4.0). */
        private long failCount;
        /** Percentage of PASS results (0..100). */
        private double passRate;
        /** Percentage of FAIL results (0..100). */
        private double failRate;
        /** Score distribution in 10 buckets (0-1, 1-2, ..., 9-10). */
        private List<ScoreBucket> distribution;
    }

    /**
     * A single bucket in the score distribution histogram.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreBucket {
        /** Human-readable range label (e.g., "0-1", "9-10"). */
        private String range;
        /** Number of submissions in this score range. */
        private long count;
        /** Percentage of total graded submissions (0..100). */
        private double percentage;
    }

    /**
     * AI OOP analysis metrics per block.
     * Includes average OOP score, violation counts, and per-criterion breakdown.
     * Criterion violations are counted when the criterion score is less than 2 (the maximum).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiOopAnalysis {
        /** Average OOP score across all AI reviews in this block (0..10). */
        private BigDecimal avgOopScore;

        /** Number of submissions with isOopViolated = true. */
        private long oopViolatedCount;
        /** Percentage of OOP-violated submissions (0..100). */
        private double oopViolatedRate;

        /** Number of submissions with detected hard-coded values. */
        private long hardCodeCount;
        /** Percentage of hard-code submissions (0..100). */
        private double hardCodeRate;

        // ─── Per-criterion violations (score < 2) ────────────────────────

        /** Number of submissions violating Encapsulation (score < 2). */
        private long encapsulationViolations;
        /** Percentage of Encapsulation violations (0..100). */
        private double encapsulationViolationRate;

        /** Number of submissions violating Inheritance & Relationships (score < 2). */
        private long inheritanceViolations;
        /** Percentage of Inheritance violations (0..100). */
        private double inheritanceViolationRate;

        /** Number of submissions violating Polymorphism (score < 2). */
        private long polymorphismViolations;
        /** Percentage of Polymorphism violations (0..100). */
        private double polymorphismViolationRate;

        /** Number of submissions violating Design Quality (score < 2). */
        private long designQualityViolations;
        /** Percentage of Design Quality violations (0..100). */
        private double designQualityViolationRate;

        /** Number of submissions violating Code Integrity / Anti-Cheat (score < 2). */
        private long codeIntegrityViolations;
        /** Percentage of Code Integrity violations (0..100). */
        private double codeIntegrityViolationRate;

        /** Dynamic per-criterion statistics (for flexible rubric mode). */
        private List<CriterionStat> criteriaStats;
    }

    /**
     * Dynamic criterion statistic entry for flexible AI rubric.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterionStat {
        /** Criterion display name, e.g. "Encapsulation". */
        private String name;
        /** Average score of this criterion across reviews. */
        private BigDecimal avgScore;
        /** Number of reviews violating this criterion. */
        private long violationCount;
        /** Percentage of violations for this criterion (0..100). */
        private double violationRate;
        /** Number of reviews where this criterion was evaluated. */
        private long sampleSize;
    }

    /**
     * Appeal and financial metrics for the block.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppealFinancialAnalysis {
        /** Total number of appeals for this block. */
        private long totalAppeals;
        /** Number of appeals in PENDING status. */
        private long pendingCount;
        /** Number of appeals in PROCESSING status. */
        private long processingCount;
        /** Number of APPROVED appeals. */
        private long approvedCount;
        /** Number of DENIED appeals. */
        private long deniedCount;
        /** Percentage of approved appeals (0..100). */
        private double approvedRate;
        /** Percentage of denied appeals (0..100). */
        private double deniedRate;

        /** Total fees collected from appeal payments (VND). */
        private BigDecimal totalFeesCollected;
        /** Total refunded to students for approved appeals (VND). */
        private BigDecimal totalRefunded;
        /** Net revenue = totalFeesCollected - totalRefunded (VND). */
        private BigDecimal netRevenue;
    }
}
