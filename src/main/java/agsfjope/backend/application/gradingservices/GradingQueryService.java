package agsfjope.backend.application.gradingservices;

import agsfjope.backend.application.dtos.responses.grading.*;
import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.repositories.grading.AIReviewRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.grading.TestCaseResultRepository;
import agsfjope.backend.core.repositories.submission.AnswerRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only query service for grading results.
 *
 * <p>Separated from {@link GradingService} (which handles async write operations)
 * to keep query concerns isolated.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradingQueryService {

    private final GradingResultRepository  gradingResultRepository;
    private final TestCaseResultRepository testCaseResultRepository;
    private final AIReviewRepository       aiReviewRepository;
    private final AnswerRepository         answerRepository;
    private final SubmissionRepository     submissionRepository;
    private final ObjectMapper             objectMapper;

    // ─── PUBLIC API ──────────────────────────────────────────────────────────

    /**
     * Returns all grading results for a block (staff view, summary level).
     *
     * @param blockId block UUID
     * @return summary list — no per-question breakdown
     */
    public List<GradingResultResponse> getBlockResults(UUID blockId) {
        return gradingResultRepository.findAllBySubmission_Block_BlockId(blockId)
                .stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    /**
     * Returns all grading results for a block WITH per-question answer breakdown.
     *
     * <p>Similar to {@link #getBlockResults(UUID)} but includes the full
     * {@code answers} list (test case results + AI OOP detail per question).
     * Used by the Excel export service to populate per-question score columns.</p>
     *
     * @param blockId block UUID
     * @return full detail list including per-answer breakdown
     */
    @Transactional(readOnly = true)
    public List<GradingResultResponse> getBlockResultsWithDetails(UUID blockId) {
        return gradingResultRepository.findAllBySubmission_Block_BlockId(blockId)
                .stream()
                .map(this::toDetailResponse)
                .collect(java.util.stream.Collectors.toList()); // mutable list — required since ExcelExportService calls .sort()
    }

    /**
     * Returns the grading result for a student's own submission in a block.
     *
     * @param blockId   block UUID
     * @param studentId the student's user ID
     * @return full grading result with per-question details
     */
    public GradingResultResponse getStudentResult(UUID blockId, UUID studentId) {
        Submission submission = submissionRepository
                .findByStudent_UserIdAndBlock_BlockId(studentId, blockId)
                .orElseThrow(() -> new NotFoundException("Bạn chưa có bài nộp cho block này."));

        GradingResult result = gradingResultRepository
                .findBySubmission_SubmissionId(submission.getSubmissionId())
                .orElseThrow(() -> new NotFoundException(
                        "Chưa có kết quả chấm bài. Vui lòng chờ."));

        return toDetailResponse(result);
    }

    /**
     * Returns the full grading result for a specific submission.
     *
     * @param submissionId  the submission UUID
     * @param requesterId   user requesting (reserved for future access control)
     * @return detailed grading result with per-question breakdown
     */
    public GradingResultResponse getSubmissionResult(UUID submissionId, UUID requesterId) {
        GradingResult result = gradingResultRepository
                .findBySubmission_SubmissionId(submissionId)
                .orElseThrow(() -> new NotFoundException(
                        "Bài này chưa được chấm hoặc không tồn tại kết quả chấm."));

        return toDetailResponse(result);
    }

    /**
     * Returns the full grading result for a specific submission without requester context.
     * Used internally by lecturer appeal detail to embed the original grading breakdown.
     *
     * @param submissionId the submission UUID
     * @return detailed grading result with per-question breakdown
     */
    @Transactional(readOnly = true)
    public GradingResultResponse getSubmissionResultDetail(UUID submissionId) {
        GradingResult result = gradingResultRepository
                .findBySubmission_SubmissionId(submissionId)
                .orElseThrow(() -> new NotFoundException(
                        "Bài này chưa được chấm hoặc không tồn tại kết quả chấm."));

        return toDetailResponse(result);
    }

    // ─── MAPPING ─────────────────────────────────────────────────────────────

    /** Summary response — no per-answer breakdown (used in block list). */
    private GradingResultResponse toSummaryResponse(GradingResult gr) {
        Submission sub = gr.getSubmission();
        User student   = sub.getStudent();
        return GradingResultResponse.builder()
                .gradingResultId(gr.getGradingResultId())
                .submissionId(sub.getSubmissionId())
                .studentId(student.getUserId())
                .studentName(student.getFullName())
                .studentCode(student.getMssv())
                .studentEmail(student.getEmail())
                .semesterName(sub.getBlock().getExam().getSemester())
                .blockName(sub.getBlock().getName())
                .gradingMode(gr.getGradingMode())
                .status(gr.getStatus())
                .totalScore(gr.getTotalScore())
                .maxScore(gr.getMaxScore())
                .testCaseScore(gr.getTestCaseScore())
                .oopScore(gr.getOopScore())
                .note(gr.getNote())
                .gradedAt(gr.getUpdatedAt())
                .answers(null) // summary only — omit per-question breakdown
                .build();
    }

    /** Full detail response (used for per-submission view). */
    private GradingResultResponse toDetailResponse(GradingResult gr) {
        Submission sub = gr.getSubmission();
        User student   = sub.getStudent();
        return GradingResultResponse.builder()
                .gradingResultId(gr.getGradingResultId())
                .submissionId(sub.getSubmissionId())
                .studentId(student.getUserId())
                .studentName(student.getFullName())
                .studentCode(student.getMssv())
                .studentEmail(student.getEmail())
                .semesterName(sub.getBlock().getExam().getSemester())
                .blockName(sub.getBlock().getName())
                .gradingMode(gr.getGradingMode())
                .status(gr.getStatus())
                .totalScore(gr.getTotalScore())
                .maxScore(gr.getMaxScore())
                .testCaseScore(gr.getTestCaseScore())
                .oopScore(gr.getOopScore())
                .note(gr.getNote())
                .gradedAt(gr.getUpdatedAt())
                .answers(buildAnswerDetails(sub.getSubmissionId()))
                .build();
    }

    private List<AnswerGradingDetail> buildAnswerDetails(UUID submissionId) {
        // Load all Answer rows for this submission so questions without testcase results
        // are still returned to the lecturer/student UI.
        List<Answer> answers = answerRepository
                .findBySubmission_SubmissionIdOrderByQuestion_QuestionNumberAsc(submissionId);

        // Load all TestCaseResults for this submission
        List<TestCaseResult> allTcResults = testCaseResultRepository
                .findByAnswer_Submission_SubmissionIdOrderByTestCase_TestCaseNumberAsc(submissionId);

        // Load all AIReviews for this submission
        List<AIReview> allAiReviews = aiReviewRepository
                .findByAnswer_Submission_SubmissionId(submissionId);

        // Group TC results by answerId
        Map<UUID, List<TestCaseResult>> tcByAnswer = allTcResults.stream()
                .collect(Collectors.groupingBy(tcr -> tcr.getAnswer().getAnswerId()));

        // Map AI reviews by answerId
        Map<UUID, AIReview> aiByAnswer = allAiReviews.stream()
                .collect(Collectors.toMap(
                        air -> air.getAnswer().getAnswerId(),
                        air -> air,
                        (a, b) -> a));

        // Build per-answer details from distinct answers, sorted by question number
        return answers.stream()
                .sorted(Comparator.comparingInt(a -> a.getQuestion().getQuestionNumber()))
                .map(answer -> {
                    UUID aid = answer.getAnswerId();
                    List<TestCaseResult> tcrs = tcByAnswer.getOrDefault(aid, List.of());
                    AIReview ai = aiByAnswer.get(aid);

                    boolean hasJar = answer.getJarFilePath() != null && !answer.getJarFilePath().isBlank();
                    boolean hasSource = answer.getSourceCodePath() != null && !answer.getSourceCodePath().isBlank();
                    boolean scoreEditable = hasJar || hasSource;
                    String scoreEditBlockedReason = scoreEditable
                            ? null
                            : "Không thể điều chỉnh điểm cho câu này vì hệ thống không tìm thấy bài làm của sinh viên. Vui lòng giữ nguyên điểm hiện tại và kiểm tra lại dữ liệu nộp bài trước khi gửi kết quả phúc khảo.";

                    // rawTestCaseScore = sum of scoreEarned from all TC results for this answer
                    BigDecimal rawTcScore = tcrs.stream()
                            .map(t -> t.getScoreEarned() != null ? t.getScoreEarned() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                                        // rawOopScore = aiReview.oopScore (AI now returns score directly on question maxScore scale)
                    BigDecimal rawOopScore = BigDecimal.ZERO;
                    if (ai != null && ai.getOopScore() != null) {
                                                BigDecimal maxScore = answer.getQuestion().getMaxScore();
                                                BigDecimal safeMax = (maxScore != null && maxScore.compareTo(BigDecimal.ZERO) > 0)
                                                                ? maxScore : BigDecimal.ZERO;
                                                rawOopScore = ai.getOopScore()
                                                                .max(BigDecimal.ZERO)
                                                                .min(safeMax)
                                                                .setScale(2, java.math.RoundingMode.HALF_UP);
                    }

                    return AnswerGradingDetail.builder()
                            .answerId(aid)
                            .questionNumber(answer.getQuestion().getQuestionNumber())
                            .questionTitle(answer.getQuestion().getTitle())
                            .maxScore(answer.getQuestion().getMaxScore())
                            // Scores persisted by pipeline in V6 columns (null if not yet re-graded)
                            .questionScore(answer.getAnswerScore())
                            .hasJar(hasJar)
                            .hasSource(hasSource)
                            .scoreEditable(scoreEditable)
                            .scoreEditBlockedReason(scoreEditBlockedReason)
                            .rawTestCaseScore(rawTcScore)
                            .rawOopScore(rawOopScore)
                            .guardRuleTriggered(answer.isGuardRuleTriggered())
                            .guardRuleNote(answer.getGuardRuleNote())
                            .testCaseResults(tcrs.stream().map(this::toTcDetail).toList())
                            .aiReview(ai != null ? toAiDetail(ai) : null)
                            .build();
                })
                .toList();
    }


    private TestCaseResultDetail toTcDetail(TestCaseResult tcr) {
        return TestCaseResultDetail.builder()
                .testCaseResultId(tcr.getTestCaseResultId())
                .testCaseNumber(tcr.getTestCase().getTestCaseNumber())
                .status(tcr.getStatus())     // TestCaseStatus enum — matches builder type
                .actualOutput(tcr.getActualOutput())
                .executionTimeMs(tcr.getExecutionTimeMs())
                .errorMessage(tcr.getErrorMessage())
                .scoreEarned(tcr.getScoreEarned())
                .build();
    }

    private AIReviewDetail toAiDetail(AIReview ai) {
        // Criteria breakdown is stored in RawResponse JSONB (no dedicated DB columns)
        BigDecimal encapsulation = null, inheritance = null, polymorphism = null,
                designQuality = null, codeIntegrity = null;
        List<String> violations = null, hardCodedValues = null;
        List<java.util.Map<String, Object>> criteriaResults = null;
        boolean aiError = false;
        String errorMessage = null;

        String raw = ai.getRawResponse();
        if (raw != null && !raw.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(raw);
                encapsulation  = toBD(root.get("encapsulation"));
                inheritance    = toBD(root.get("inheritance"));
                polymorphism   = toBD(root.get("polymorphism"));
                designQuality  = toBD(root.get("designQuality"));
                codeIntegrity  = toBD(root.get("codeIntegrity"));
                violations      = toStringList(root.get("violations"));
                hardCodedValues = toStringList(root.get("hardCodedValues"));
                criteriaResults = toMapList(root.get("criteriaResults"));
                aiError         = root.path("aiError").asBoolean(false);
                errorMessage    = root.path("errorMessage").asText(null);
            } catch (Exception e) {
                log.warn("Could not parse rawResponse for AIReview {}: {}", ai.getAiReviewId(), e.getMessage());
            }
        }

        return AIReviewDetail.builder()
                .aiReviewId(ai.getAiReviewId())
                .oopScore(ai.getOopScore())
                .encapsulationScore(encapsulation)
                .inheritanceScore(inheritance)
                .polymorphismScore(polymorphism)
                .designQualityScore(designQuality)
                .codeIntegrityScore(codeIntegrity)
                .criteriaResults(criteriaResults)
                .violations(violations)
                .hardCodedValues(hardCodedValues)
                .comment(ai.getComment())
                .aiError(aiError)
                .errorMessage(errorMessage)
                .oopViolated(Boolean.TRUE.equals(ai.getIsOopViolated()))
                .build();
    }

    private static BigDecimal toBD(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try { return new BigDecimal(node.asText()); } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) return new ArrayList<>();
        List<String> list = new ArrayList<>();
        node.forEach(n -> { if (!n.isNull()) list.add(n.asText()); });
        return list;
    }

    private List<java.util.Map<String, Object>> toMapList(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) return new ArrayList<>();
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        node.forEach(n -> {
            if (n != null && n.isObject()) {
                try {
                    list.add(objectMapper.convertValue(n, java.util.Map.class));
                } catch (Exception ignored) {
                }
            }
        });
        return list;
    }
}
