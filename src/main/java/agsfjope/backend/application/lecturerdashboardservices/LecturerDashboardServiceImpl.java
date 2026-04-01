package agsfjope.backend.application.lecturerdashboardservices;

import agsfjope.backend.application.dtos.responses.lecturerdashboard.AssignedAppealResponse;
import agsfjope.backend.application.dtos.responses.lecturerdashboard.LecturerDashboardOverviewResponse;
import agsfjope.backend.application.dtos.responses.lecturerdashboard.ReviewStatsResponse;
import agsfjope.backend.application.dtos.responses.lecturerdashboard.UpcomingDeadlineResponse;
import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link LecturerDashboardService}.
 * <p>
 * All data is scoped to the currently logged-in lecturer's UUID.
 * All read operations are wrapped in a read-only transaction for consistency.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LecturerDashboardServiceImpl implements LecturerDashboardService {

    private final AppealRepository appealRepository;

    /** Hours threshold for the "TRONG 2 NGÀY TỚI" urgency label. */
    private static final long URGENT_HOURS = 48L;

    // ─── Overview ───────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     */
    @Override
    public LecturerDashboardOverviewResponse getOverview(UUID lecturerId) {
        long assignedAppeals   = appealRepository.countByAssignedLecturerAndStatus(lecturerId, "PROCESSING");
        long completedReviews  = appealRepository.countCompletedReviewsByAssignedLecturer(lecturerId);
        long overdueAppeals    = appealRepository.countOverdueByAssignedLecturer(lecturerId, OffsetDateTime.now());

        return LecturerDashboardOverviewResponse.builder()
                .assignedAppeals(assignedAppeals)
                .completedReviews(completedReviews)
                .overdueAppeals(overdueAppeals)
                .build();
    }

    // ─── Assigned Appeals ───────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AssignedAppealResponse> getAssignedAppeals(UUID lecturerId, int limit, String status) {
        boolean filtered = status != null && !status.isBlank();
        List<Appeal> appeals = filtered
                ? appealRepository.findByAssignedLecturerAndStatusOrderByAssignedAtDesc(
                        lecturerId, status.toUpperCase(), PageRequest.of(0, limit))
                : appealRepository.findByAssignedLecturerOrderByAssignedAtDesc(
                        lecturerId, PageRequest.of(0, limit));

        List<AssignedAppealResponse> result = new ArrayList<>();
        for (Appeal appeal : appeals) {
            String studentName = appeal.getStudent() != null ? appeal.getStudent().getFullName() : "";
            String studentMssv = appeal.getStudent() != null ? appeal.getStudent().getMssv() : "";
            String examName    = "";
            String blockName   = "";
            if (appeal.getSubmission() != null) {
                if (appeal.getSubmission().getBlock() != null) {
                    blockName = appeal.getSubmission().getBlock().getName();
                    if (appeal.getSubmission().getBlock().getExam() != null) {
                        examName = appeal.getSubmission().getBlock().getExam().getName();
                    }
                }
            }
            result.add(AssignedAppealResponse.builder()
                    .appealId(appeal.getAppealId())
                    .studentName(studentName)
                    .studentMssv(studentMssv)
                    .examName(examName)
                    .blockName(blockName)
                    .assignedDate(appeal.getAssignedAt())
                    .deadline(appeal.getDeadlineAt())
                    .status(appeal.getStatus())
                    .build());
        }
        return result;
    }

    // ─── Upcoming Deadlines ─────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UpcomingDeadlineResponse> getUpcomingDeadlines(UUID lecturerId, int limit) {
        List<Appeal> appeals = appealRepository.findProcessingByAssignedLecturerOrderByDeadlineAsc(
                lecturerId, PageRequest.of(0, limit));

        OffsetDateTime now = OffsetDateTime.now();
        List<UpcomingDeadlineResponse> result = new ArrayList<>();
        for (Appeal appeal : appeals) {
            String examName    = "";
            String studentName = appeal.getStudent() != null ? appeal.getStudent().getFullName() : "";
            if (appeal.getSubmission() != null
                    && appeal.getSubmission().getBlock() != null
                    && appeal.getSubmission().getBlock().getExam() != null) {
                examName = appeal.getSubmission().getBlock().getExam().getName();
            }

            String urgencyLabel = resolveUrgencyLabel(appeal.getDeadlineAt(), now);

            result.add(UpcomingDeadlineResponse.builder()
                    .appealId(appeal.getAppealId())
                    .examName(examName)
                    .studentName(studentName)
                    .deadline(appeal.getDeadlineAt())
                    .urgencyLabel(urgencyLabel)
                    .build());
        }
        return result;
    }

    // ─── Review Stats ────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     */
    @Override
    public ReviewStatsResponse getReviewStats(UUID lecturerId) {
        long approvedCount = appealRepository.countApprovedByAssignedLecturer(lecturerId);
        long deniedCount   = appealRepository.countDeniedByAssignedLecturer(lecturerId);
        long totalReviews  = appealRepository.countCompletedReviewsByAssignedLecturer(lecturerId);

        double approvedPct = totalReviews > 0 ? Math.round(approvedCount * 1000.0 / totalReviews) / 10.0 : 0.0;
        double deniedPct   = totalReviews > 0 ? Math.round(deniedCount   * 1000.0 / totalReviews) / 10.0 : 0.0;

        return ReviewStatsResponse.builder()
                .totalReviews(totalReviews)
                .approvedCount(approvedCount)
                .approvedPercentage(approvedPct)
                .deniedCount(deniedCount)
                .deniedPercentage(deniedPct)
                .build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Determines the urgency label for a deadline relative to now.
     *
     * @param deadline the appeal's deadline
     * @param now      the current timestamp
     * @return one of "CẦN XỬ LÝ NGAY", "TRONG 2 NGÀY TỚI", or "SẮP TỚI"
     */
    private String resolveUrgencyLabel(OffsetDateTime deadline, OffsetDateTime now) {
        if (deadline == null) return "SẮP TỚI";
        if (deadline.isBefore(now)) return "CẦN XỬ LÝ NGAY";
        long hoursUntil = java.time.Duration.between(now, deadline).toHours();
        return hoursUntil <= URGENT_HOURS ? "TRONG 2 NGÀY TỚI" : "SẮP TỚI";
    }
}
