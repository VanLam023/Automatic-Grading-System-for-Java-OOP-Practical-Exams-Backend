package agsfjope.backend.application.staffdashboardservices;

import agsfjope.backend.application.dtos.responses.staffdashboard.GradeDistributionResponse;
import agsfjope.backend.application.dtos.responses.staffdashboard.GradeDistributionResponse.ScoreRange;
import agsfjope.backend.application.dtos.responses.staffdashboard.PendingAppealResponse;
import agsfjope.backend.application.dtos.responses.staffdashboard.RecentExamResponse;
import agsfjope.backend.application.dtos.responses.staffdashboard.StaffDashboardOverviewResponse;
import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.entities.Exam;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.enums.ExamStatus;
import agsfjope.backend.core.enums.SubmissionStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.exam.ExamRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link StaffDashboardService}.
 * <p>
 * Aggregates data from multiple repositories to power the four sections
 * of the Staff Dashboard. All read operations are wrapped in a read-only
 * transaction for consistency.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffDashboardServiceImpl implements StaffDashboardService {

    private final ExamRepository          examRepository;
    private final SubmissionRepository    submissionRepository;
    private final GradingResultRepository gradingResultRepository;
    private final AppealRepository        appealRepository;

    /**
     * Score range definitions for the grade distribution chart.
     * Each entry: { label, minScore (inclusive), maxScore (exclusive) }.
     * The last bucket (9-10) uses 10.01 as upper bound to include 10.00.
     */
    private static final String[][] SCORE_RANGES = {
            {"0-4",  "0.00",  "4.00"},
            {"4-6",  "4.00",  "6.00"},
            {"6-8",  "6.00",  "8.00"},
            {"8-9",  "8.00",  "9.00"},
            {"9-10", "9.00", "10.01"}
    };

    // ─── Overview ───────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     */
    @Override
    public StaffDashboardOverviewResponse getOverview(String semester) {
        boolean filtered = semester != null && !semester.isBlank();

        long activeExams = filtered
                ? examRepository.countByStatusAndDeletedAtIsNullAndSemester(ExamStatus.ONGOING, semester)
                : examRepository.countByStatusAndDeletedAtIsNull(ExamStatus.ONGOING);

        long totalSubmissions = filtered
                ? submissionRepository.countBySemester(semester)
                : submissionRepository.count();

        long gradedSubmissions = filtered
                ? submissionRepository.countByStatusAndSemester(SubmissionStatus.GRADED, semester)
                : submissionRepository.countByStatus(SubmissionStatus.GRADED);

        long pendingAppeals = filtered
                ? appealRepository.countByStatusAndSemester(AppealStatus.PENDING.name(), semester)
                : appealRepository.countByStatus(AppealStatus.PENDING.name());

        return StaffDashboardOverviewResponse.builder()
                .activeExams(activeExams)
                .totalSubmissions(totalSubmissions)
                .gradedSubmissions(gradedSubmissions)
                .pendingAppeals(pendingAppeals)
                .build();
    }

    // ─── Recent Exams ───────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     */
    @Override
    public List<RecentExamResponse> getRecentExams(int limit, String semester) {
        boolean filtered = semester != null && !semester.isBlank();
        PageRequest page = PageRequest.of(0, limit);

        List<Exam> exams = filtered
                ? examRepository.findAllBySemesterAndDeletedAtIsNullOrderByCreatedAtDesc(semester, page)
                : examRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc(page);

        List<RecentExamResponse> result = new ArrayList<>();
        for (Exam exam : exams) {
            result.add(RecentExamResponse.builder()
                    .examId(exam.getExamId())
                    .name(exam.getName())
                    .semester(exam.getSemester())
                    .status(exam.getStatus())
                    .build());
        }
        return result;
    }

    // ─── Grade Distribution ─────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     */
    @Override
    public GradeDistributionResponse getGradeDistribution(String semester) {
        boolean filtered = semester != null && !semester.isBlank();

        long totalGraded = filtered
                ? gradingResultRepository.countAllBySemester(semester)
                : gradingResultRepository.countAll();

        List<ScoreRange> ranges = new ArrayList<>();
        for (String[] range : SCORE_RANGES) {
            BigDecimal min = new BigDecimal(range[1]);
            BigDecimal max = new BigDecimal(range[2]);
            long count = filtered
                    ? gradingResultRepository.countByScoreRangeAndSemester(min, max, semester)
                    : gradingResultRepository.countByScoreRange(min, max);
            double percentage = totalGraded > 0
                    ? Math.round(count * 1000.0 / totalGraded) / 10.0
                    : 0.0;
            ranges.add(ScoreRange.builder()
                    .label(range[0])
                    .count(count)
                    .percentage(percentage)
                    .build());
        }

        return GradeDistributionResponse.builder()
                .totalGraded(totalGraded)
                .ranges(ranges)
                .build();
    }

    // ─── Pending Appeals ────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PendingAppealResponse> getPendingAppeals(int limit, String semester) {
        boolean filtered = semester != null && !semester.isBlank();
        PageRequest page = PageRequest.of(0, limit);

        List<Appeal> appeals = filtered
                ? appealRepository.findPendingAndProcessingBySemesterOrderByCreatedAtDesc(semester, page)
                : appealRepository.findPendingAndProcessingOrderByCreatedAtDesc(page);


        List<PendingAppealResponse> result = new ArrayList<>();
        for (Appeal appeal : appeals) {
            String studentName = appeal.getStudent() != null ? appeal.getStudent().getFullName() : "";
            String studentMssv = appeal.getStudent() != null ? appeal.getStudent().getMssv() : "";
            String examName    = "";
            if (appeal.getSubmission() != null
                    && appeal.getSubmission().getBlock() != null
                    && appeal.getSubmission().getBlock().getExam() != null) {
                examName = appeal.getSubmission().getBlock().getExam().getName();
            }

            result.add(PendingAppealResponse.builder()
                    .appealId(appeal.getAppealId())
                    .studentName(studentName)
                    .studentMssv(studentMssv)
                    .examName(examName)
                    .status(appeal.getStatus())
                    .createdAt(appeal.getCreatedAt())
                    .build());
        }
        return result;
    }
}
