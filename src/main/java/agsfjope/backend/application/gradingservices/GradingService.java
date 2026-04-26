package agsfjope.backend.application.gradingservices;

import agsfjope.backend.application.dtos.requests.grading.TriggerGradingRequest;
import agsfjope.backend.application.dtos.responses.grading.GradingProgressResponse;
import agsfjope.backend.core.entities.Submission;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.GradingMode;
import agsfjope.backend.core.enums.SubmissionStatus;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

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
// [Old] @RequiredArgsConstructor
public class GradingService {

    /**
     * [PERF-STEP3] Semaphore that caps the number of submissions being graded in parallel.
     * With 3 permits: max 3 submissions × 5 questions × 2 AI calls = 30 AI calls at once,
     * which stays within Gemini's RPM limits on paid tier.
     * Reduce to 2 if rate-limit errors occur; increase to 4 if API quota allows.
     */
    private static final int MAX_PARALLEL_SUBMISSIONS = 3;
    private final Semaphore submissionSemaphore = new Semaphore(MAX_PARALLEL_SUBMISSIONS, true);

    /** Blocks currently running grading — prevents double-trigger on same block. */
    private final Set<UUID> activeBlockGradings = ConcurrentHashMap.newKeySet();

    /**
     * Blocks requested to stop — checked by {@link GradingPipelineService}
     * before each per-question step. Thread-safe.
     */
    private final Set<UUID> cancelledBlocks = ConcurrentHashMap.newKeySet();

    private final GradingPipelineService pipelineService;
    private final DeterministicGradingService deterministicGradingService;
    private final SubmissionRepository   submissionRepository;

    // [PERF-STEP3] Thread pool injected for running each submission in parallel
    private final Executor submissionExecutor;

    // [PERF-STEP3] Constructor injection (replaces @RequiredArgsConstructor) to support @Qualifier
    public GradingService(GradingPipelineService pipelineService,
                          DeterministicGradingService deterministicGradingService,
                          SubmissionRepository submissionRepository,
                          @Qualifier("submissionExecutor") Executor submissionExecutor) {
        this.pipelineService      = pipelineService;
        this.deterministicGradingService = deterministicGradingService;
        this.submissionRepository = submissionRepository;
        this.submissionExecutor   = submissionExecutor;
    }

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

        // Xoá cờ huỷ còn sót lại từ lần chấm trước (nếu có)
        // Clear any leftover cancel flag from a previous stop
        cancelledBlocks.remove(blockId);

        // Kiểm tra xem block này có đang được chấm không — nếu có thì ném lỗi ngay
        // (ConcurrentHashMap.newKeySet().add() trả về false nếu phần tử đã tồn tại)
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

            // [PERF-STEP3] Grade submissions in PARALLEL using CompletableFuture + Semaphore.
            // Phương pháp MỚI: chấm song song nhiều bài cùng lúc thay vì tuần tự từng bài.
            // Semaphore giới hạn số bài chạy đồng thời để tránh vượt rate limit của Gemini API.
            // Old sequential for loop (kept for reference):
            // [OLD] for (Submission submission : targets) {
            // [OLD]     if (cancelledBlocks.contains(blockId)) { break; }
            // [OLD]     submission.setStatus(SubmissionStatus.GRADING);
            // [OLD]     submissionRepository.save(submission);
            // [OLD]     pipelineService.grade(submission, triggeredBy, cancelledBlocks);
            // [OLD] }
            //
            // New: each submission runs on its own thread (submissionExecutor).
            // Semaphore(3) ensures at most MAX_PARALLEL_SUBMISSIONS run concurrently
            // to avoid Gemini rate limits (max 3 × 5 questions × 2 AI calls = 30 calls).

            // Danh sách các tác vụ chấm bài đang chạy bất đồng bộ
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (Submission submission : targets) {
                // ── CANCELLATION CHECK (between submissions) ──────────────────────
                // Dừng sớm: nếu admin đã bấm Stop thì không gửi thêm tác vụ mới
                if (cancelledBlocks.contains(blockId)) {
                    log.info("Grading for block {} stopped before submitting all tasks.", blockId);
                    break;
                }

                // Cần dùng biến final để lambda có thể capture
                final Submission sub = submission;
                // Mỗi bài được giao cho một thread riêng trong submissionExecutor
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // Chờ lấy permit từ Semaphore — nếu đã có MAX_PARALLEL_SUBMISSIONS bài đang chạy
                        // thì thread này sẽ TỰ ĐỘNG CHỜ cho đến khi có slot trống
                        // Block until a permit is available (max MAX_PARALLEL_SUBMISSIONS running)
                        submissionSemaphore.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Semaphore acquire interrupted for submission {}", sub.getSubmissionId());
                        return;
                    }
                    try {
                        // Kiểm tra lại sau khi chờ permit — có thể block đã bị huỷ trong thời gian chờ
                        // Re-check cancellation after potentially waiting for a permit
                        if (cancelledBlocks.contains(blockId)) {
                            log.info("Skipping submission {} — block {} cancelled while waiting.",
                                    sub.getSubmissionId(), blockId);
                            return;
                        }
                        // Cập nhật trạng thái sang GRADING ngay để màn hình tiến độ phản ánh real-time
                        // Persist GRADING status so progress endpoint reflects real-time state
                        sub.setStatus(SubmissionStatus.GRADING);
                        submissionRepository.save(sub);

                        // [MODE_5 BRANCH]
                        if (sub.getBlock().getExam().getGradingMode() == GradingMode.MODE_5) {
                            deterministicGradingService.grade(sub, triggeredBy, cancelledBlocks);
                        } else {
                            // [ORIGINAL PIPELINE]
                            pipelineService.grade(sub, triggeredBy, cancelledBlocks);
                        }

                    } catch (GradingCancelledException e) {
                        log.info("Grading cancelled mid-submission {} in block {}",
                                sub.getSubmissionId(), blockId);
                        // @Transactional on grade() was rolled back — partial TC results gone from DB.
                        submissionRepository.findById(sub.getSubmissionId())
                                .filter(s -> s.getStatus() != SubmissionStatus.SUBMITTED)
                                .ifPresent(s -> {
                                    s.setStatus(SubmissionStatus.SUBMITTED);
                                    submissionRepository.save(s);
                                });
                    } catch (Exception e) {
                        log.error("Grading failed for submission {}: {}",
                                sub.getSubmissionId(), e.getMessage(), e);
                        sub.setStatus(SubmissionStatus.SUBMITTED);
                        submissionRepository.save(sub);
                    } finally {
                        submissionSemaphore.release(); // always release, even on error
                    }
                }, submissionExecutor);

                futures.add(future);
            }

            // Chờ TẤT CẢ các tác vụ chấm bài hoàn thành trước khi giải phóng block lock
            // Nếu không có dòng này, cleanup() sẽ chạy ngay trong khi các bài vẫn đang chấm
            // Wait for ALL in-flight submissions to finish before releasing the block lock
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

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
