package agsfjope.backend.application.staffdashboardservices;

import agsfjope.backend.application.dtos.responses.staffdashboard.GradeDistributionResponse;
import agsfjope.backend.application.dtos.responses.staffdashboard.GradeDistributionResponse.ScoreRange;
import agsfjope.backend.application.dtos.responses.staffdashboard.PendingAppealResponse;
import agsfjope.backend.application.dtos.responses.staffdashboard.RecentExamResponse;
import agsfjope.backend.application.dtos.responses.staffdashboard.StaffDashboardOverviewResponse;
import agsfjope.backend.core.entities.Exam;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.enums.ExamStatus;
import agsfjope.backend.core.enums.SubmissionStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.appeal.projections.PendingAppealRowProjection;
import agsfjope.backend.core.repositories.exam.ExamRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.grading.projections.ScoreBucketCountProjection;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private final ExamRepository examRepository;
    private final SubmissionRepository submissionRepository;
    private final GradingResultRepository gradingResultRepository;
    private final AppealRepository appealRepository;

    /**
     * Score range definitions for the grade distribution chart.
     * Kept aligned with the current agreed design: 0-4, 4-6, 6-8, 8-9, 9-10.
     */
    private static final String[] SCORE_RANGE_LABELS = {
            "0-4",
            "4-6",
            "6-8",
            "8-9",
            "9-10"
    };

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

        // Keep field name for backward compatibility, but value now matches dashboard table logic:
        // open appeals = PENDING + PROCESSING.
        long pendingAppeals = filtered
                ? appealRepository.countOpenAppealsBySemester(semester)
                : appealRepository.countOpenAppeals();

        return StaffDashboardOverviewResponse.builder()
                .activeExams(activeExams)
                .totalSubmissions(totalSubmissions)
                .gradedSubmissions(gradedSubmissions)
                .pendingAppeals(pendingAppeals)
                .build();
    }

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

    @Override
    public GradeDistributionResponse getGradeDistribution(String semester) {
        boolean filtered = semester != null && !semester.isBlank();

        long totalGraded = filtered
                ? gradingResultRepository.countAllBySemester(semester)
                : gradingResultRepository.countAll();

        List<ScoreBucketCountProjection> bucketRows = filtered
                ? gradingResultRepository.aggregateScoreBucketsBySemester(semester)
                : gradingResultRepository.aggregateScoreBuckets();

        Map<String, Long> bucketCounts = new HashMap<>();
        for (ScoreBucketCountProjection row : bucketRows) {
            bucketCounts.put(row.getBucketLabel(), row.getBucketCount());
        }

        List<ScoreRange> ranges = new ArrayList<>();
        for (String label : SCORE_RANGE_LABELS) {
            long count = bucketCounts.getOrDefault(label, 0L);
            double percentage = totalGraded > 0
                    ? Math.round(count * 1000.0 / totalGraded) / 10.0
                    : 0.0;

            ranges.add(ScoreRange.builder()
                    .label(label)
                    .count(count)
                    .percentage(percentage)
                    .build());
        }

        return GradeDistributionResponse.builder()
                .totalGraded(totalGraded)
                .ranges(ranges)
                .build();
    }

    @Override
    public List<PendingAppealResponse> getPendingAppeals(int limit, String semester) {
        boolean filtered = semester != null && !semester.isBlank();
        PageRequest page = PageRequest.of(0, limit);

        List<PendingAppealRowProjection> appeals = filtered
                ? appealRepository.findPendingAndProcessingRowsBySemesterOrderByCreatedAtDesc(semester, page)
                : appealRepository.findPendingAndProcessingRowsOrderByCreatedAtDesc(page);

        List<PendingAppealResponse> result = new ArrayList<>();
        for (PendingAppealRowProjection appeal : appeals) {
            result.add(PendingAppealResponse.builder()
                    .appealId(appeal.getAppealId())
                    .studentName(appeal.getStudentName())
                    .studentMssv(appeal.getStudentMssv())
                    .examName(appeal.getExamName())
                    .status(AppealStatus.valueOf(appeal.getStatus()))
                    .createdAt(appeal.getCreatedAt())
                    .build());
        }
        return result;
    }
}