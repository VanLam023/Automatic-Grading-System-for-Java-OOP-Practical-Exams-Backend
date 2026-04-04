package agsfjope.backend.application.examstatisticsservices.impl;

import agsfjope.backend.application.dtos.responses.statistics.BlockStatisticsResponse;
import agsfjope.backend.application.dtos.responses.statistics.BlockStatisticsResponse.*;
import agsfjope.backend.application.examstatisticsservices.ExamStatisticsService;
import agsfjope.backend.core.entities.AIReview;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.block.BlockRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.core.repositories.grading.AIReviewRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link ExamStatisticsService} — PROC-006.
 *
 * <p>Generates block-level statistics by aggregating data from
 * GradingResult, AIReview, Appeal, and WalletTransaction tables.</p>
 *
 * <h3>Important validation:</h3>
 * <p>Validates that the blockId belongs to the given examId to prevent
 * cross-exam data leakage (user's explicit requirement).</p>
 *
 * <h3>AI OOP criteria violations:</h3>
 * <p>Criteria breakdown (encapsulation, inheritance, polymorphism, designQuality, codeIntegrity)
 * is stored in {@code AIReview.rawResponse} (JSONB), not in dedicated DB columns.
 * This service parses the JSON at runtime to count violations (score &lt; 2).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExamStatisticsServiceImpl implements ExamStatisticsService {

    /** Threshold: a criterion score below this value is considered a violation. */
    private static final BigDecimal VIOLATION_THRESHOLD = new BigDecimal("2");

    private final BlockRepository          blockRepository;
    private final SubmissionRepository     submissionRepository;
    private final GradingResultRepository  gradingResultRepository;
    private final AIReviewRepository       aiReviewRepository;
    private final AppealRepository         appealRepository;
    private final SystemConfigRepository   systemConfigRepository;
    private final ObjectMapper             objectMapper;

    @Override
    public BlockStatisticsResponse getBlockStatistics(UUID examId, UUID blockId) {
        // ── VALIDATION: block phải thuộc đúng exam (tránh lấy nhầm block của exam khác) ──
        // Dùng derived query để tránh LazyInitializationException khi load Block.exam
        if (!blockRepository.existsById(blockId)) {
            throw new NotFoundException("Block không tồn tại.");
        }
        if (!blockRepository.existsByBlockIdAndExam_ExamId(blockId, examId)) {
            throw new NotFoundException("Block không thuộc kỳ thi này.");
        }

        // ── (1) SUBMISSION OVERVIEW ──────────────────────────────────────────
        // Đếm tổng bài nộp (mọi trạng thái) và bài đã chấm xong
        long totalSubmissions = submissionRepository.countByBlock_BlockId(blockId);
        long gradedSubmissions = gradingResultRepository.countGradedByBlockId(blockId);

        // ── (2) SCORE ANALYSIS ──────────────────────────────────────────────
        // Tính các chỉ số điểm: trung bình / cao nhất / thấp nhất / pass-fail / histogram
        ScoreAnalysis scoreAnalysis = buildScoreAnalysis(blockId, gradedSubmissions);

        // ── (3) AI OOP ANALYSIS ─────────────────────────────────────────────
        // Load toàn bộ AIReview của block → parse JSON rawResponse → đếm vi phạm
        AiOopAnalysis aiOopAnalysis = buildAiOopAnalysis(blockId, gradedSubmissions);

        // ── (4) APPEAL & FINANCIAL ──────────────────────────────────────────
        // Đếm đơn phúc khảo theo trạng thái, tính doanh thu từ wallet transactions
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

    /**
     * Builds score analysis metrics for a block.
     * Queries avg/max/min/pass/fail from GradingResultRepository and builds histogram.
     */
    private ScoreAnalysis buildScoreAnalysis(UUID blockId, long gradedCount) {
        // Lấy avg/max/min từ native query — trả về Double (nullable) nếu chưa có bài chấm
        Double avgRaw = gradingResultRepository.avgScoreByBlockId(blockId);
        Double maxRaw = gradingResultRepository.maxScoreByBlockId(blockId);
        Double minRaw = gradingResultRepository.minScoreByBlockId(blockId);

        // Convert Double → BigDecimal; trả về ZERO nếu null (chưa có bài chấm)
        BigDecimal avgScore = avgRaw != null ? BigDecimal.valueOf(avgRaw).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal maxScore = maxRaw != null ? BigDecimal.valueOf(maxRaw).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal minScore = minRaw != null ? BigDecimal.valueOf(minRaw).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        long passCount = gradingResultRepository.countPassByBlockId(blockId);
        long failCount = gradingResultRepository.countFailByBlockId(blockId);

        // Tính tỷ lệ % (tránh chia cho 0)
        double passRate = gradedCount > 0 ? round(passCount * 100.0 / gradedCount) : 0;
        double failRate = gradedCount > 0 ? round(failCount * 100.0 / gradedCount) : 0;

        // Build histogram 10 buckets (0-1, 1-2, ..., 9-10)
        List<ScoreBucket> distribution = buildScoreDistribution(blockId, gradedCount);

        return ScoreAnalysis.builder()
                .avgScore(avgScore != null ? avgScore.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .maxScore(maxScore != null ? maxScore.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .minScore(minScore != null ? minScore.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .passCount(passCount)
                .failCount(failCount)
                .passRate(passRate)
                .failRate(failRate)
                .distribution(distribution)
                .build();
    }

    /**
     * Builds score distribution histogram with 10 buckets.
     * Bucket ranges: [0,1), [1,2), ..., [8,9), [9,10.01) — last bucket includes 10.0.
     */
    private List<ScoreBucket> buildScoreDistribution(UUID blockId, long gradedCount) {
        List<ScoreBucket> buckets = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            BigDecimal lower = BigDecimal.valueOf(i);
            // Bucket cuối (9-10) dùng 10.01 làm upper bound để bao gồm điểm 10.0
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

    // ─── AI OOP ANALYSIS ────────────────────────────────────────────────────

    /**
     * Builds AI OOP analysis by loading all AIReviews for the block and parsing rawResponse.
     * Counts violations per criterion (score < 2), OOP violations, and hard-coded values.
     */
    private AiOopAnalysis buildAiOopAnalysis(UUID blockId, long gradedCount) {
        // Load tất cả AI Reviews trong block — JPQL join qua Answer → Submission → Block
        List<AIReview> reviews = aiReviewRepository.findAllByBlockId(blockId);

        if (reviews.isEmpty()) {
            // Không có AI review nào → trả về tất cả giá trị = 0
            return AiOopAnalysis.builder()
                    .avgOopScore(BigDecimal.ZERO)
                    .oopViolatedCount(0).oopViolatedRate(0)
                    .hardCodeCount(0).hardCodeRate(0)
                    .encapsulationViolations(0).encapsulationViolationRate(0)
                    .inheritanceViolations(0).inheritanceViolationRate(0)
                    .polymorphismViolations(0).polymorphismViolationRate(0)
                    .designQualityViolations(0).designQualityViolationRate(0)
                    .codeIntegrityViolations(0).codeIntegrityViolationRate(0)
                    .build();
        }

        long totalReviews = reviews.size();

        // Tính OOP score trung bình
        BigDecimal sumOop = BigDecimal.ZERO;
        long oopViolatedCount = 0;
        long hardCodeCount = 0;

        // Đếm vi phạm từng tiêu chí (score < 2)
        long encViolations = 0, inhViolations = 0, polyViolations = 0;
        long dqViolations = 0, ciViolations = 0;

        for (AIReview review : reviews) {
            // Tính tổng OOP score (dùng để tính trung bình)
            if (review.getOopScore() != null) {
                sumOop = sumOop.add(review.getOopScore());
            }

            // Đếm bài bị OOP violated
            if (Boolean.TRUE.equals(review.getIsOopViolated())) {
                oopViolatedCount++;
            }

            // Parse rawResponse JSONB để lấy criteria breakdown và hardCodedValues
            String raw = review.getRawResponse();
            if (raw == null || raw.isBlank()) continue;

            try {
                JsonNode root = objectMapper.readTree(raw);

                // Kiểm tra hard-coded values — đếm bài nào có mảng hardCodedValues không rỗng
                JsonNode hardCodedNode = root.get("hardCodedValues");
                if (hardCodedNode != null && hardCodedNode.isArray() && !hardCodedNode.isEmpty()) {
                    hardCodeCount++;
                }

                // Đếm vi phạm từng tiêu chí: nếu điểm < 2 thì tính là vi phạm
                // (Mỗi tiêu chí max = 2, ngưỡng vi phạm = < 2)
                if (isCriterionViolated(root, "encapsulation"))  encViolations++;
                if (isCriterionViolated(root, "inheritance"))    inhViolations++;
                if (isCriterionViolated(root, "polymorphism"))   polyViolations++;
                if (isCriterionViolated(root, "designQuality"))  dqViolations++;
                if (isCriterionViolated(root, "codeIntegrity"))  ciViolations++;

            } catch (Exception e) {
                // JSON parse thất bại — bỏ qua review này, không ảnh hưởng thống kê
                log.warn("Failed to parse rawResponse for AIReview {}: {}",
                        review.getAiReviewId(), e.getMessage());
            }
        }

        // Tính trung bình OOP score
        BigDecimal avgOop = sumOop.divide(BigDecimal.valueOf(totalReviews), 2, RoundingMode.HALF_UP);

        // Dùng gradedCount (tổng bài đã chấm) làm mẫu số cho tỷ lệ %
        // vì 1 submission có thể có nhiều AIReview (1 per question), ta dùng totalReviews
        // cho các chỉ số per-review, nhưng gom theo submission bằng gradedCount
        long denominator = totalReviews; // dùng totalReviews vì mỗi AIReview = 1 câu hỏi

        return AiOopAnalysis.builder()
                .avgOopScore(avgOop)
                .oopViolatedCount(oopViolatedCount)
                .oopViolatedRate(denominator > 0 ? round(oopViolatedCount * 100.0 / denominator) : 0)
                .hardCodeCount(hardCodeCount)
                .hardCodeRate(denominator > 0 ? round(hardCodeCount * 100.0 / denominator) : 0)
                .encapsulationViolations(encViolations)
                .encapsulationViolationRate(denominator > 0 ? round(encViolations * 100.0 / denominator) : 0)
                .inheritanceViolations(inhViolations)
                .inheritanceViolationRate(denominator > 0 ? round(inhViolations * 100.0 / denominator) : 0)
                .polymorphismViolations(polyViolations)
                .polymorphismViolationRate(denominator > 0 ? round(polyViolations * 100.0 / denominator) : 0)
                .designQualityViolations(dqViolations)
                .designQualityViolationRate(denominator > 0 ? round(dqViolations * 100.0 / denominator) : 0)
                .codeIntegrityViolations(ciViolations)
                .codeIntegrityViolationRate(denominator > 0 ? round(ciViolations * 100.0 / denominator) : 0)
                .build();
    }

    /**
     * Checks if a specific criterion is violated (score < 2) in the AI review JSON.
     *
     * @param root          parsed JSON root node of rawResponse
     * @param criterionName the criterion key (e.g., "encapsulation")
     * @return true if the criterion score exists and is less than 2
     */
    private boolean isCriterionViolated(JsonNode root, String criterionName) {
        JsonNode node = root.get(criterionName);
        if (node == null || node.isNull()) return false;
        try {
            BigDecimal score = new BigDecimal(node.asText());
            return score.compareTo(VIOLATION_THRESHOLD) < 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ─── APPEAL & FINANCIAL ─────────────────────────────────────────────────

    /**
     * Builds appeal and financial analysis for a block.
     * Counts appeals by status and calculates revenue from wallet transaction amounts.
     */
    private AppealFinancialAnalysis buildAppealFinancial(UUID blockId) {
        // Đếm tổng đơn phúc khảo và từng trạng thái
        long totalAppeals   = appealRepository.countByBlockId(blockId);
        long pendingCount   = appealRepository.countByBlockIdAndStatus(blockId, "PENDING");
        long processingCount = appealRepository.countByBlockIdAndStatus(blockId, "PROCESSING");
        long approvedCount  = appealRepository.countByBlockIdAndStatus(blockId, "APPROVED");
        long deniedCount    = appealRepository.countByBlockIdAndStatus(blockId, "DENIED");

        // Tính tỷ lệ % dựa trên tổng đơn đã có kết quả (approved + denied)
        long decidedCount = approvedCount + deniedCount;
        double approvedRate = decidedCount > 0 ? round(approvedCount * 100.0 / decidedCount) : 0;
        double deniedRate   = decidedCount > 0 ? round(deniedCount * 100.0 / decidedCount) : 0;

        // Tính doanh thu từ phúc khảo:
        // totalFeesCollected = số đơn đã thanh toán × phí mỗi đơn (lấy từ tổng APPEAL_PAYMENT)
        // totalRefunded = số đơn approved × phí mỗi đơn (lấy từ tổng APPEAL_REFUND)
        // Hiện tại chưa có query trực tiếp WalletTransaction theo block →
        // lấy phí từ SystemConfigs (mặc định 200,000 VND)
        BigDecimal appealFee = getAppealFee();
        // Tổng phí thu = tổng đơn (trừ PENDING_PAYMENT, CANCELLED) × phí
        long paidAppeals = totalAppeals
                - appealRepository.countByBlockIdAndStatus(blockId, "PENDING_PAYMENT")
                - appealRepository.countByBlockIdAndStatus(blockId, "CANCELLED");
        BigDecimal totalFees = appealFee.multiply(BigDecimal.valueOf(Math.max(0, paidAppeals)));
        BigDecimal totalRefunded = appealFee.multiply(BigDecimal.valueOf(approvedCount));
        BigDecimal netRevenue = totalFees.subtract(totalRefunded);

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
                    try {
                        return new BigDecimal(config.getConfigValue());
                    } catch (Exception e) {
                        return new BigDecimal("200000");
                    }
                })
                .orElse(new BigDecimal("200000"));
    }

    // ─── UTILITIES ──────────────────────────────────────────────────────────

    /**
     * Rounds a double to 1 decimal place for percentage display.
     */
    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
