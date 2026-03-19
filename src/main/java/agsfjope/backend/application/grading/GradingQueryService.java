package agsfjope.backend.application.grading;

import agsfjope.backend.application.dtos.responses.grading.*;
import agsfjope.backend.core.entities.*;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.repositories.grading.AIReviewRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.grading.TestCaseResultRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
@Service
@RequiredArgsConstructor
public class GradingQueryService {

    private final GradingResultRepository  gradingResultRepository;
    private final TestCaseResultRepository testCaseResultRepository;
    private final AIReviewRepository       aiReviewRepository;
    private final SubmissionRepository     submissionRepository;

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
        return allTcResults.stream()
                .map(TestCaseResult::getAnswer)
                .distinct()
                .sorted(Comparator.comparingInt(a -> a.getQuestion().getQuestionNumber()))
                .map(answer -> {
                    UUID aid = answer.getAnswerId();
                    List<TestCaseResult> tcrs = tcByAnswer.getOrDefault(aid, List.of());
                    AIReview ai = aiByAnswer.get(aid);

                    return AnswerGradingDetail.builder()
                            .answerId(aid)
                            .questionNumber(answer.getQuestion().getQuestionNumber())
                            .questionTitle(answer.getQuestion().getTitle())
                            .maxScore(answer.getQuestion().getMaxScore())
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
        return AIReviewDetail.builder()
                .aiReviewId(ai.getAiReviewId())
                .oopScore(ai.getOopScore())
                .comment(ai.getComment())
                .oopViolated(Boolean.TRUE.equals(ai.getIsOopViolated()))
                .build();
    }
}
