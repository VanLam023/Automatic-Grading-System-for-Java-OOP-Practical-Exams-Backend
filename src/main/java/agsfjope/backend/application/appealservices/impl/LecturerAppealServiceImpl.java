package agsfjope.backend.application.appealservices.impl;

import agsfjope.backend.application.appealservices.LecturerAppealService;
import agsfjope.backend.application.dtos.requests.appeal.ReviewAppealRequest;
import agsfjope.backend.application.dtos.responses.appeal.LecturerAppealDetailResponse;
import agsfjope.backend.application.dtos.responses.appeal.LecturerAppealListItemResponse;
import agsfjope.backend.application.dtos.responses.appeal.LecturerAppealOverviewResponse;
import agsfjope.backend.application.dtos.responses.appeal.LecturerAppealPageResponse;
import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.entities.GradingResult;
import agsfjope.backend.core.entities.Submission;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.configuration.storage.MinioConfig;
import agsfjope.backend.infrastructure.storage.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LecturerAppealServiceImpl implements LecturerAppealService {

    private final AppealRepository appealRepository;
    private final GradingResultRepository gradingResultRepository;
    private final MinioService minioService;
    private final MinioConfig minioConfig;

    @Override
    @Transactional(readOnly = true)
    public LecturerAppealPageResponse getAppeals(UUID lecturerId, String status, String keyword, int page, int size) {
        log.info("[Lecturer] Lấy danh sách phân công: lecturer={}, status={}, keyword={}", lecturerId, status, keyword);

        String statusParam  = (status == null || status.isBlank()) ? null : status.toUpperCase();
        String keywordParam = (keyword == null) ? "" : keyword.trim();

        // Stats
        LecturerAppealOverviewResponse overview = buildOverview(lecturerId);

        // Paged data
        Page<Appeal> pageResult = appealRepository.searchAppealsForLecturer(
                lecturerId, statusParam, keywordParam, PageRequest.of(page, size));

        List<LecturerAppealListItemResponse> list = pageResult.getContent()
                .stream()
                .map(this::toListItem)
                .toList();

        return LecturerAppealPageResponse.builder()
                .overview(overview)
                .appeals(list)
                .currentPage(pageResult.getNumber())
                .totalPages(pageResult.getTotalPages())
                .totalElements(pageResult.getTotalElements())
                .pageSize(pageResult.getSize())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public LecturerAppealDetailResponse getAppealDetail(UUID lecturerId, UUID appealId) {
        log.info("[Lecturer] Lấy chi tiết chấm: lecturer={}, appeal={}", lecturerId, appealId);
        Appeal appeal = getAndValidateOwnership(lecturerId, appealId);
        return toDetailResponse(appeal);
    }

    @Override
    @Transactional
    public LecturerAppealDetailResponse submitReview(UUID lecturerId, UUID appealId, ReviewAppealRequest request) {
        log.info("[Lecturer] Submit review: lecturer={}, appeal={}, newScore={}", 
                lecturerId, appealId, request.getNewScore());

        Appeal appeal = getAndValidateOwnership(lecturerId, appealId);

        if (appeal.getStatus() != AppealStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Theo quy trình, giảng viên chỉ có thể nộp báo cáo chấm phúc khảo cho các đơn đang ở trạng thái PROCESSING. " +
                    "Trạng thái hiện tại: " + appeal.getStatus());
        }

        // Cập nhật thông tin chấm điểm
        appeal.setNewScore(request.getNewScore());
        appeal.setNewQuestionScores(request.getNewQuestionScores());
        appeal.setLecturerComment(request.getLecturerComment());
        
        // Cập nhật status báo hiệu giảng viên gút kết quả
        appeal.setStatus(AppealStatus.COMPLETED);

        Appeal saved = appealRepository.save(appeal);
        return toDetailResponse(saved);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    @Override
    public InputStream downloadSubmission(UUID lecturerId, UUID appealId) {
        log.info("[Lecturer] Download submission: lecturer={}, appeal={}", lecturerId, appealId);
        Appeal appeal = getAndValidateOwnership(lecturerId, appealId);
        
        if (appeal.getSubmission() == null || appeal.getSubmission().getFilePath() == null) {
            throw new IllegalStateException("Không tìm thấy file bài làm đính kèm trong cơ sở dữ liệu");
        }
        
        return minioService.downloadFile(
                minioConfig.getBucket().getSubmissions(),
                appeal.getSubmission().getFilePath()
        );
    }

    private Appeal getAndValidateOwnership(UUID lecturerId, UUID appealId) {
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn phúc khảo hợp lệ"));

        if (appeal.getAssignedLecturer() == null || !appeal.getAssignedLecturer().getUserId().equals(lecturerId)) {
            throw new IllegalStateException("Bạn không có quyền truy cập hoặc chấm cho đơn phúc khảo này.");
        }
        return appeal;
    }

    private LecturerAppealOverviewResponse buildOverview(UUID lecturerId) {
        long totalAssigned = appealRepository.countByAssignedLecturerAndStatus(lecturerId, "PROCESSING")
                           + appealRepository.countByAssignedLecturerAndStatus(lecturerId, "COMPLETED")
                           + appealRepository.countByAssignedLecturerAndStatus(lecturerId, "APPROVED")
                           + appealRepository.countByAssignedLecturerAndStatus(lecturerId, "DENIED");

        long inReview = appealRepository.countByAssignedLecturerAndStatus(lecturerId, "PROCESSING");
        
        long completed = appealRepository.countByAssignedLecturerAndStatus(lecturerId, "COMPLETED")
                       + appealRepository.countByAssignedLecturerAndStatus(lecturerId, "APPROVED")
                       + appealRepository.countByAssignedLecturerAndStatus(lecturerId, "DENIED");

        long overdue = appealRepository.countOverdueByAssignedLecturer(lecturerId, OffsetDateTime.now());

        return LecturerAppealOverviewResponse.builder()
                .totalAssigned(totalAssigned)
                .inReview(inReview)
                .completed(completed)
                .overdue(overdue)
                .build();
    }

    private LecturerAppealListItemResponse toListItem(Appeal a) {
        String examName = "", semester = "", blockName = "";
        try {
            examName  = a.getSubmission().getBlock().getExam().getName();
            semester  = a.getSubmission().getBlock().getExam().getSemester();
            blockName = a.getSubmission().getBlock().getName();
        } catch (Exception ignored) {}

        BigDecimal originalScore = getOriginalScore(a.getSubmission());
        boolean isOverdue = false;
        if (a.getStatus() == AppealStatus.PROCESSING && a.getDeadlineAt() != null) {
            isOverdue = a.getDeadlineAt().isBefore(OffsetDateTime.now());
        }

        String appealCode = "#PK-" + a.getCreatedAt().getYear()
                + "-" + a.getAppealId().toString().substring(0, 4).toUpperCase();

        return LecturerAppealListItemResponse.builder()
                .appealId(a.getAppealId())
                .appealCode(appealCode)
                .studentName(a.getStudent().getFullName())
                .studentMssv(a.getStudent().getMssv())
                .examName(examName)
                .semester(semester)
                .blockName(blockName)
                .reason(a.getReason())
                .status(a.getStatus())
                .originalScore(originalScore)
                .newScore(a.getNewScore())
                .createdAt(a.getCreatedAt())
                .deadlineAt(a.getDeadlineAt())
                .isOverdue(isOverdue)
                .build();
    }

    private LecturerAppealDetailResponse toDetailResponse(Appeal a) {
        String examName = "", semester = "", blockName = "", fileName = "";
        UUID submissionId = null;
        try {
            examName     = a.getSubmission().getBlock().getExam().getName();
            semester     = a.getSubmission().getBlock().getExam().getSemester();
            blockName    = a.getSubmission().getBlock().getName();
            fileName     = a.getSubmission().getFileName();
            submissionId = a.getSubmission().getSubmissionId();
        } catch (Exception ignored) {}

        GradingResult gradingResult = gradingResultRepository
                .findBySubmission_SubmissionId(submissionId).orElse(null);

        BigDecimal originalScore = BigDecimal.ZERO;
        BigDecimal testCaseScore = BigDecimal.ZERO;
        BigDecimal oopScore = BigDecimal.ZERO;

        if (gradingResult != null) {
            originalScore = gradingResult.getTotalScore();
            testCaseScore = gradingResult.getTestCaseScore();
            oopScore = gradingResult.getOopScore();
        }

        String appealCode = "#PK-" + a.getCreatedAt().getYear()
                + "-" + a.getAppealId().toString().substring(0, 4).toUpperCase();

        return LecturerAppealDetailResponse.builder()
                .appealId(a.getAppealId())
                .appealCode(appealCode)
                .status(a.getStatus())
                .reason(a.getReason())
                .lecturerComment(a.getLecturerComment())
                .originalScore(originalScore)
                .testCaseScore(testCaseScore)
                .oopScore(oopScore)
                .newScore(a.getNewScore())
                .newQuestionScores(a.getNewQuestionScores())
                .createdAt(a.getCreatedAt())
                .deadlineAt(a.getDeadlineAt())
                .studentName(a.getStudent().getFullName())
                .studentMssv(a.getStudent().getMssv())
                .examName(examName)
                .semester(semester)
                .blockName(blockName)
                .submissionId(submissionId)
                .submissionFileName(fileName)
                .build();
    }

    private BigDecimal getOriginalScore(Submission submission) {
        if (submission == null) return BigDecimal.ZERO;
        return gradingResultRepository.findBySubmission_SubmissionId(submission.getSubmissionId())
                .map(GradingResult::getTotalScore)
                .orElse(BigDecimal.ZERO);
    }
}
