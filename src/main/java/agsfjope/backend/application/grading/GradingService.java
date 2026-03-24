package agsfjope.backend.application.grading;

import agsfjope.backend.application.dtos.requests.grading.TriggerGradingRequest;
import agsfjope.backend.application.dtos.responses.grading.GradingProgressResponse;
import agsfjope.backend.core.entities.Submission;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.SubmissionStatus;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public-facing grading orchestrator.
 *
 * <h3>Responsibilities:</h3>
 * <ul>
 *   <li>Accepts grading trigger requests from the controller (3 modes)</li>
 *   <li>Resolves which submissions to grade based on {@link TriggerGradingRequest}</li>
 *   <li>Guards against double-grading on GRADE_ALL via in-memory block lock</li>
 *   <li>Supports cancellation: {@link #stopGrading(UUID)} sets a cancel flag;
 *       the pipeline checks between each answer and stops early</li>
 *   <li>Dispatches each submission to {@link GradingPipelineService} on a thread pool</li>
 *   <li>Provides progress polling via {@link #getProgress(UUID)}</li>
 * </ul>
 *
 * <h3>Stop Grading Behaviour:</h3>
 * <ul>
 *   <li>Calling {@link #stopGrading(UUID)} marks a block as cancelled.</li>
 *   <li>The pipeline checks this flag before starting each answer (question).</li>
 *   <li>Any submission currently mid-grading (GRADING status) will be reset to
 *       SUBMITTED so it can be re-graded later.</li>
 *   <li>Submissions fully graded before the stop signal keep their GRADED status.</li>
 * </ul>
 *
 * <p>Grading runs {@code @Async} on the {@code gradingTaskExecutor} thread pool.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradingService {

    /** Blocks currently running grading — prevents double-trigger on same block. */
    private final Set<UUID> activeBlockGradings = ConcurrentHashMap.newKeySet();

    /**
     * Blocks requested to stop — checked by {@link GradingPipelineService}
     * before each per-question step. Thread-safe.
     */
    private final Set<UUID> cancelledBlocks = ConcurrentHashMap.newKeySet();

    private final GradingPipelineService pipelineService;
    private final SubmissionRepository   submissionRepository;

    // ─── PUBLIC API ──────────────────────────────────────────────────────────

    /**
     * Triggers grading for one or more submissions.
     * Runs asynchronously — returns immediately after dispatching tasks.
     *
     * @param blockId     block to grade submissions for
     * @param request     specifies which submissions to grade
     * @param triggeredBy the staff user who triggered grading
     */
    @Async("gradingTaskExecutor")
    public void triggerGrading(UUID blockId, TriggerGradingRequest request, User triggeredBy) {
        log.warn("[GRADING] triggerGrading called for block={} by user={}", blockId,
                triggeredBy != null ? triggeredBy.getUsername() : "unknown");

        // Clear any leftover cancel flag from a previous stop
        cancelledBlocks.remove(blockId);

        // Guard against concurrent trigger on same block (ALL/SELECTED/SINGLE)
        if (!activeBlockGradings.add(blockId)) {
            throw new GradingAlreadyInProgressException(
                    "Hệ thống đang chấm bài cho block này. Vui lòng chờ hoàn tất hoặc dừng tiến trình hiện tại.");
        }

        try {
            List<Submission> targets = resolveTargets(blockId, request);

            if (targets.isEmpty()) {
                log.info("Grading triggered for block {} but no eligible submissions found", blockId);
                return;
            }

            boolean isGradeAll = request.getSubmissionIds() == null;
            String modeLabel = isGradeAll ? "GRADE_ALL"
                    : (targets.size() == 1 ? "GRADE_SINGLE" : "GRADE_SELECTED");
            log.info("Grading triggered: block={}, mode={}, count={}", blockId, modeLabel, targets.size());

            for (Submission submission : targets) {
                // ── CANCELLATION CHECK (between submissions) ──────────────────────
                if (cancelledBlocks.contains(blockId)) {
                    log.info("Grading for block {} was stopped. Resetting remaining submissions.", blockId);
                    // The current submission hasn't started yet — nothing to reset
                    break;
                }

                try {
                    // Persist GRADING immediately so progress endpoint can reflect in real-time
                    submission.setStatus(SubmissionStatus.GRADING);
                    submissionRepository.save(submission);

                    pipelineService.grade(submission, triggeredBy, cancelledBlocks);
                } catch (GradingCancelledException e) {
                    log.info("Grading cancelled mid-submission {} in block {}",
                            submission.getSubmissionId(), blockId);
                    // @Transactional on grade() was rolled back, so:
                    //   ✅ partial TC results are gone from DB
                    //   ✅ submission.status in DB is already SUBMITTED (pre-grade value)
                    // The in-memory object is stale (status=GRADING), so re-fetch to confirm.
                    submissionRepository.findById(submission.getSubmissionId())
                            .filter(s -> s.getStatus() != agsfjope.backend.core.enums.SubmissionStatus.SUBMITTED)
                            .ifPresent(s -> {
                                s.setStatus(agsfjope.backend.core.enums.SubmissionStatus.SUBMITTED);
                                submissionRepository.save(s);
                            });
                    break;
                } catch (Exception e) {
                    log.error("Grading failed for submission {}: {}",
                            submission.getSubmissionId(), e.getMessage(), e);
                    // Reset to SUBMITTED so staff can re-grade
                    submission.setStatus(SubmissionStatus.SUBMITTED);
                    submissionRepository.save(submission);
                }
            }

            log.info("Grading batch ended for block {}", blockId);
        } finally {
            cleanup(blockId);
        }
    }

    /**
     * Signals the grading loop for a block to stop after the current answer finishes.
     *
     * <p>This is a cooperative cancellation — it does NOT forcibly kill running threads.
     * The pipeline checks the flag before each question (answer), so the stop takes
     * effect within one question's grading time.</p>
     *
     * @param blockId block to stop grading for
     * @throws IllegalStateException if the block is not currently being graded
     */
    public void stopGrading(UUID blockId) {
        if (!activeBlockGradings.contains(blockId)) {
            throw new IllegalStateException(
                    "Quá trình chấm bài hiện không chạy cho block này.");
        }
        cancelledBlocks.add(blockId);
        log.info("Stop signal sent for block {}", blockId);
    }

    /**
     * Returns current grading progress for a block (polling endpoint).
     *
     * @param blockId block UUID
     * @return progress snapshot
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public GradingProgressResponse getProgress(UUID blockId) {
        long total   = submissionRepository.countByBlock_BlockId(blockId);
        long graded  = submissionRepository.countByBlock_BlockIdAndStatus(
                blockId, SubmissionStatus.GRADED);
        long grading = submissionRepository.countByBlock_BlockIdAndStatus(
                blockId, SubmissionStatus.GRADING);
        long pending = Math.max(0, total - graded - grading);

        int percent = total == 0 ? 0 : (int) Math.round((double) graded / total * 100);
        boolean inProgress = activeBlockGradings.contains(blockId) || grading > 0;
        boolean stopping   = cancelledBlocks.contains(blockId);

        String status;
        if (stopping)    status = "STOPPING";
        else if (inProgress) status = "IN_PROGRESS";
        else if (graded == total && total > 0) status = "COMPLETED";
        else             status = "PENDING";

        return GradingProgressResponse.builder()
                .blockId(blockId)
                .totalSubmissions(total)
                .gradedCount(graded)
                .gradingCount(grading)
                .pendingCount(pending)
                .progressPercent(percent)
                .status(status)
                .build();
    }

    /**
     * Returns true if the block is currently being graded.
     */
    public boolean isGrading(UUID blockId) {
        return activeBlockGradings.contains(blockId);
    }

    // ─── PRIVATE ─────────────────────────────────────────────────────────────

    private List<Submission> resolveTargets(UUID blockId, TriggerGradingRequest request) {
        List<UUID> ids = request.getSubmissionIds();

        // submissionIds == null OR empty array → GRADE_ALL
        // Include GRADING (stuck) and GRADED (re-grade) alongside SUBMITTED.
        // Two separate queries per status to avoid NAMED_ENUM IN-clause issues.
        if (ids == null || ids.isEmpty()) {
            List<Submission> submitted = submissionRepository.findByBlock_BlockIdAndStatus(
                    blockId, SubmissionStatus.SUBMITTED);
            List<Submission> grading   = submissionRepository.findByBlock_BlockIdAndStatus(
                    blockId, SubmissionStatus.GRADING);
            List<Submission> graded    = submissionRepository.findByBlock_BlockIdAndStatus(
                    blockId, SubmissionStatus.GRADED);
            log.warn("[GRADING] resolveTargets: found {} SUBMITTED + {} GRADING + {} GRADED for block {}",
                    submitted.size(), grading.size(), graded.size(), blockId);
            List<Submission> merged = new java.util.ArrayList<>(submitted);
            merged.addAll(grading);
            merged.addAll(graded);
            return merged;
        }

        // submissionIds = [id1, id2, ...] → GRADE_SELECTED or GRADE_SINGLE
        // Accept SUBMITTED, GRADING (stuck), and GRADED (re-grade request).
        return submissionRepository.findAllById(ids).stream()
                .filter(s -> s.getBlock().getBlockId().equals(blockId))
                .filter(s -> s.getStatus() == SubmissionStatus.SUBMITTED
                          || s.getStatus() == SubmissionStatus.GRADING
                          || s.getStatus() == SubmissionStatus.GRADED)
                .toList();
    }


    private void cleanup(UUID blockId) {
        activeBlockGradings.remove(blockId);
        cancelledBlocks.remove(blockId);
    }
}
