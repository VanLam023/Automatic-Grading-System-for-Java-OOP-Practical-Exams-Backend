package agsfjope.backend.application.examstatisticsservices.impl;

import agsfjope.backend.application.dtos.responses.statistics.BlockStatisticsResponse;
import agsfjope.backend.application.dtos.responses.statistics.BlockStatisticsResponse.*;
import agsfjope.backend.application.examstatisticsservices.ExamStatisticsService;
import agsfjope.backend.core.entities.CriteriaResult;
import agsfjope.backend.core.enums.CriterionType;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.block.BlockRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.core.repositories.grading.CriteriaResultRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Implementation of {@link ExamStatisticsService} — PROC-006.
 *
 * <p>Generates block-level statistics by aggregating data from
 * GradingResult, CriteriaResult, Appeal, and WalletTransaction tables.</p>
 *
 * <h3>OOP criteria violations:</h3>
 * <p>Criteria breakdown (encapsulation, inheritance, polymorphism) is now derived
 * from {@code CriteriaResult} rows (JavaParser/deterministic grading output),
 * grouped by {@link CriterionType}:
 * <ul>
 *   <li>Encapsulation  → FIELD_CHECK, GETTER_SETTER</li>
 *   <li>Inheritance    → EXTENDS_CHECK, IMPLEMENTS_CHECK, CLASS_EXISTS, INTERFACE_EXISTS</li>
 *   <li>Polymorphism   → METHOD_SIGNATURE, CONSTRUCTOR_CHECK, NAMING_CONVENTION</li>
 * </ul>
 * Hard-coded value detection is not persisted in CriteriaResult; that field is set to 0.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExamStatisticsServiceImpl implements ExamStatisticsService {

    // CriterionType → OOP category mapping
    private static final Set<CriterionType> ENCAPSULATION_TYPES = Set.of(
            CriterionType.FIELD_CHECK, CriterionType.GETTER_SETTER
    );
    private static final Set<CriterionType> INHERITANCE_TYPES = Set.of(
            CriterionType.EXTENDS_CHECK, CriterionType.IMPLEMENTS_CHECK,
            CriterionType.CLASS_EXISTS, CriterionType.INTERFACE_EXISTS
    );
    private static final Set<CriterionType> POLYMORPHISM_TYPES = Set.of(
            CriterionType.METHOD_SIGNATURE, CriterionType.CONSTRUCTOR_CHECK,
            CriterionType.NAMING_CONVENTION
    );

    private final BlockRepository          blockRepository;
    private final SubmissionRepository     submissionRepository;
    private final GradingResultRepository  gradingResultRepository;
    private final CriteriaResultRepository criteriaResultRepository;
    private final AppealRepository         appealRepository;
    private final SystemConfigRepository   systemConfigRepository;

    @Override
    public BlockStatisticsResponse getBlockStatistics(UUID examId, UUID blockId) {
        if (!blockRepository.existsById(blockId)) {
            throw new NotFoundException("Block không tồn tại.");
        }
        if (!blockRepository.existsByBlockIdAndExam_ExamId(blockId, examId)) {
            throw new NotFoundException("Block không thuộc kỳ thi này.");
        }

        long totalSubmissions  = submissionRepository.countByBlock_BlockId(blockId);
        long gradedSubmissions = gradingResultRepository.countGradedByBlockId(blockId);

        ScoreAnalysis         scoreAnalysis   = buildScoreAnalysis(blockId, gradedSubmissions);
        AiOopAnalysis         aiOopAnalysis   = buildAiOopAnalysis(blockId);
        AppealFinancialAnalysis appealFinancial = buildAppealFinancial(blockId);

        return BlockStatisticsResponse.builder()
                .totalSubmissions(totalSubmissions)
                .gradedSubmissions(gradedSubmissions)
                .scoreAnalysis(scoreAnalysis)
                .aiOopAnalysis(aiOopAnalysis)
                .appealFinancial(appealFinancial)
                .build();
    }

    // ─── SCORE ANALYSIS ─────────────────────────────────────────────────────

    private ScoreAnalysis buildScoreAnalysis(UUID blockId, long gradedCount) {
        Double avgRaw = gradingResultRepository.avgScoreByBlockId(blockId);
        Double maxRaw = gradingResultRepository.maxScoreByBlockId(blockId);
        Double minRaw = gradingResultRepository.minScoreByBlockId(blockId);

        BigDecimal avgScore = avgRaw != null ? BigDecimal.valueOf(avgRaw).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal maxScore = maxRaw != null ? BigDecimal.valueOf(maxRaw).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal minScore = minRaw != null ? BigDecimal.valueOf(minRaw).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        long passCount = gradingResultRepository.countPassByBlockId(blockId);
        long failCount = gradingResultRepository.countFailByBlockId(blockId);

        double passRate = gradedCount > 0 ? round(passCount * 100.0 / gradedCount) : 0;
        double failRate = gradedCount > 0 ? round(failCount * 100.0 / gradedCount) : 0;

        List<ScoreBucket> distribution = buildScoreDistribution(blockId, gradedCount);

        return ScoreAnalysis.builder()
                .avgScore(avgScore.setScale(2, RoundingMode.HALF_UP))
                .maxScore(maxScore.setScale(2, RoundingMode.HALF_UP))
                .minScore(minScore.setScale(2, RoundingMode.HALF_UP))
                .passCount(passCount)
                .failCount(failCount)
                .passRate(passRate)
                .failRate(failRate)
                .distribution(distribution)
                .build();
    }

    private List<ScoreBucket> buildScoreDistribution(UUID blockId, long gradedCount) {
        List<ScoreBucket> buckets = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            BigDecimal lower = BigDecimal.valueOf(i);
            BigDecimal upper = (i == 9) ? new BigDecimal("10.01") : BigDecimal.valueOf(i + 1);
            long count = gradingResultRepository.countByBlockIdAndScoreRange(blockId, lower, upper);
            double percentage = gradedCount > 0 ? round(count * 100.0 / gradedCount) : 0;
            buckets.add(ScoreBucket.builder()
                    .range(i + "-" + (i + 1))
                    .count(count)
                    .percentage(percentage)
                    .build());
        }
        return buckets;
    }

    // ─── OOP ANALYSIS (từ CriteriaResult — JavaParser/Deterministic) ─────────

    /**
     * Builds OOP analysis by loading all {@link CriteriaResult} rows for the block.
     *
     * <p>Replaces the previous approach that parsed {@code AIReview.rawResponse} JSON.
     * Now uses deterministic grading results persisted by {@code DeterministicOopScorer}.</p>
     *
     * <p>Violation mapping by {@link CriterionType}:
     * <ul>
     *   <li>Encapsulation  → FIELD_CHECK, GETTER_SETTER</li>
     *   <li>Inheritance    → EXTENDS_CHECK, IMPLEMENTS_CHECK, CLASS_EXISTS, INTERFACE_EXISTS</li>
     *   <li>Polymorphism   → METHOD_SIGNATURE, CONSTRUCTOR_CHECK, NAMING_CONVENTION</li>
     * </ul>
     * </p>
     */
    private AiOopAnalysis buildAiOopAnalysis(UUID blockId) {
        List<CriteriaResult> results = criteriaResultRepository.findAllByBlockId(blockId);

        if (results.isEmpty()) {
            return AiOopAnalysis.builder()
                    .avgOopScore(BigDecimal.ZERO)
                    .oopViolatedCount(0).oopViolatedRate(0)
                    .hardCodeCount(0).hardCodeRate(0)
                    .encapsulationViolations(0).encapsulationViolationRate(0)
                    .inheritanceViolations(0).inheritanceViolationRate(0)
                    .polymorphismViolations(0).polymorphismViolationRate(0)
                    .designQualityViolations(0).designQualityViolationRate(0)
                    .codeIntegrityViolations(0).codeIntegrityViolationRate(0)
                    .criteriaStats(List.of())
                    .build();
        }

        long totalResults      = results.size();
        long encViolations     = 0;
        long inhViolations     = 0;
        long polyViolations    = 0;
        long oopViolatedCount  = 0; // bài có ít nhất 1 criterion failed
        BigDecimal sumEarned   = BigDecimal.ZERO;
        BigDecimal sumMax      = BigDecimal.ZERO;

        // Per-criterion dynamic stats (keyed by description)
        Map<String, CriterionAccumulator> criteriaMap = new LinkedHashMap<>();

        // Track which answers have at least 1 failed criterion
        Set<UUID> violatedAnswers = new HashSet<>();

        for (CriteriaResult cr : results) {
            if (cr.getCriteria() == null) continue;

            CriterionType type        = cr.getCriteria().getCriterionType();
            String        description = cr.getCriteria().getDescription();
            BigDecimal    maxScore    = cr.getCriteria().getMaxScore();
            BigDecimal    earned      = cr.getEarnedScore() != null ? cr.getEarnedScore() : BigDecimal.ZERO;
            boolean       failed      = !cr.isPassed();

            // Tính avg OOP score
            sumEarned = sumEarned.add(earned);
            if (maxScore != null) sumMax = sumMax.add(maxScore);

            // Đếm vi phạm theo OOP category
            if (type != null) {
                if (ENCAPSULATION_TYPES.contains(type) && failed)  encViolations++;
                if (INHERITANCE_TYPES.contains(type)   && failed)  inhViolations++;
                if (POLYMORPHISM_TYPES.contains(type)  && failed)  polyViolations++;
            }

            // Track violated answers
            if (failed && cr.getAnswer() != null) {
                violatedAnswers.add(cr.getAnswer().getAnswerId());
            }

            // Dynamic criterion map
            boolean violated = failed;
            criteriaMap.computeIfAbsent(description, k -> new CriterionAccumulator())
                    .add(earned, maxScore, violated);
        }

        oopViolatedCount = violatedAnswers.size();

        // avgOopScore = tỷ lệ % điểm OOP đạt được (scale 0-10)
        BigDecimal avgOop = BigDecimal.ZERO;
        if (sumMax.compareTo(BigDecimal.ZERO) > 0) {
            avgOop = sumEarned.divide(sumMax, 4, RoundingMode.HALF_UP)
                              .multiply(BigDecimal.TEN)
                              .setScale(2, RoundingMode.HALF_UP);
        }

        long denominator = totalResults;

        List<CriterionStat> criteriaStats = criteriaMap.entrySet().stream()
                .map(e -> toCriterionStat(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CriterionStat::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return AiOopAnalysis.builder()
                .avgOopScore(avgOop)
                .oopViolatedCount(oopViolatedCount)
                .oopViolatedRate(denominator > 0 ? round(oopViolatedCount * 100.0 / denominator) : 0)
                .hardCodeCount(0)     // hard-code detection không lưu vào CriteriaResult
                .hardCodeRate(0)
                .encapsulationViolations(encViolations)
                .encapsulationViolationRate(denominator > 0 ? round(encViolations * 100.0 / denominator) : 0)
                .inheritanceViolations(inhViolations)
                .inheritanceViolationRate(denominator > 0 ? round(inhViolations * 100.0 / denominator) : 0)
                .polymorphismViolations(polyViolations)
                .polymorphismViolationRate(denominator > 0 ? round(polyViolations * 100.0 / denominator) : 0)
                .designQualityViolations(0)    // không còn category này trong deterministic grading
                .designQualityViolationRate(0)
                .codeIntegrityViolations(0)    // không còn category này trong deterministic grading
                .codeIntegrityViolationRate(0)
                .criteriaStats(criteriaStats)
                .build();
    }

    private CriterionStat toCriterionStat(String name, CriterionAccumulator acc) {
        long sample = acc.sampleSize;
        BigDecimal avg = BigDecimal.ZERO;
        if (sample > 0 && acc.sumMax.compareTo(BigDecimal.ZERO) > 0) {
            avg = acc.sumEarned.divide(acc.sumMax, 4, RoundingMode.HALF_UP)
                               .multiply(acc.sumMax.divide(BigDecimal.valueOf(sample), 4, RoundingMode.HALF_UP))
                               .setScale(2, RoundingMode.HALF_UP);
            // Simpler: avg earned score per evaluation
            avg = acc.sumEarned.divide(BigDecimal.valueOf(sample), 2, RoundingMode.HALF_UP);
        }
        double rate = sample > 0 ? round(acc.violationCount * 100.0 / sample) : 0;

        return CriterionStat.builder()
                .name(name)
                .avgScore(avg)
                .violationCount(acc.violationCount)
                .violationRate(rate)
                .sampleSize(sample)
                .build();
    }

    private static final class CriterionAccumulator {
        private BigDecimal sumEarned = BigDecimal.ZERO;
        private BigDecimal sumMax    = BigDecimal.ZERO;
        private long violationCount  = 0;
        private long sampleSize      = 0;

        private void add(BigDecimal earned, BigDecimal max, boolean violated) {
            if (earned != null) sumEarned = sumEarned.add(earned);
            if (max    != null) sumMax    = sumMax.add(max);
            sampleSize++;
            if (violated) violationCount++;
        }
    }

    // ─── APPEAL & FINANCIAL ─────────────────────────────────────────────────

    private AppealFinancialAnalysis buildAppealFinancial(UUID blockId) {
        long totalAppeals    = appealRepository.countByBlockId(blockId);
        long pendingCount    = appealRepository.countByBlockIdAndStatus(blockId, "PENDING");
        long processingCount = appealRepository.countByBlockIdAndStatus(blockId, "PROCESSING");
        long approvedCount   = appealRepository.countByBlockIdAndStatus(blockId, "APPROVED");
        long deniedCount     = appealRepository.countByBlockIdAndStatus(blockId, "DENIED");

        long decidedCount   = approvedCount + deniedCount;
        double approvedRate = decidedCount > 0 ? round(approvedCount * 100.0 / decidedCount) : 0;
        double deniedRate   = decidedCount > 0 ? round(deniedCount   * 100.0 / decidedCount) : 0;

        BigDecimal appealFee = getAppealFee();
        long paidAppeals = totalAppeals
                - appealRepository.countByBlockIdAndStatus(blockId, "PENDING_PAYMENT")
                - appealRepository.countByBlockIdAndStatus(blockId, "CANCELLED");
        BigDecimal totalFees    = appealFee.multiply(BigDecimal.valueOf(Math.max(0, paidAppeals)));
        BigDecimal totalRefunded = appealFee.multiply(BigDecimal.valueOf(approvedCount));
        BigDecimal netRevenue   = totalFees.subtract(totalRefunded);

        return AppealFinancialAnalysis.builder()
                .totalAppeals(totalAppeals)
                .pendingCount(pendingCount)
                .processingCount(processingCount)
                .approvedCount(approvedCount)
                .deniedCount(deniedCount)
                .approvedRate(approvedRate)
                .deniedRate(deniedRate)
                .totalFeesCollected(totalFees)
                .totalRefunded(totalRefunded)
                .netRevenue(netRevenue)
                .build();
    }

    private BigDecimal getAppealFee() {
        return systemConfigRepository.findByConfigKey("APPEAL_FEE")
                .map(config -> {
                    try { return new BigDecimal(config.getConfigValue()); }
                    catch (Exception e) { return new BigDecimal("200000"); }
                })
                .orElse(new BigDecimal("200000"));
    }

    // ─── UTILITIES ──────────────────────────────────────────────────────────

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
