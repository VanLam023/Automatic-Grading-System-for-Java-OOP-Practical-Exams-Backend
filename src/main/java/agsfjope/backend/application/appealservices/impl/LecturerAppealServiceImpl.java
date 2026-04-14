package agsfjope.backend.application.appealservices.impl;

import agsfjope.backend.application.appealservices.LecturerAppealService;
import agsfjope.backend.application.dtos.requests.appeal.ReviewAppealRequest;
import agsfjope.backend.application.dtos.responses.appeal.LecturerAppealDetailResponse;
import agsfjope.backend.application.dtos.responses.appeal.LecturerAppealListItemResponse;
import agsfjope.backend.application.dtos.responses.appeal.LecturerAppealOverviewResponse;
import agsfjope.backend.application.dtos.responses.appeal.LecturerAppealPageResponse;
import agsfjope.backend.application.dtos.responses.grading.GradingResultResponse;
import agsfjope.backend.application.gradingservices.GradingQueryService;
import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.entities.Answer;
import agsfjope.backend.core.entities.GradingResult;
import agsfjope.backend.core.entities.Submission;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.submission.AnswerRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LecturerAppealServiceImpl implements LecturerAppealService {

    private final AppealRepository appealRepository;
    private final GradingResultRepository gradingResultRepository;
    private final GradingQueryService gradingQueryService;
    private final AnswerRepository answerRepository;
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

        validateRequestedQuestionScoreUpdates(appeal, request);

        // Cập nhật thông tin chấm điểm
        appeal.setNewScore(request.getNewScore());
        appeal.setNewQuestionScores(request.getNewQuestionScores());
        appeal.setLecturerComment(request.getLecturerComment());

        // Cập nhật status báo hiệu giảng viên gút kết quả
        appeal.setStatus(AppealStatus.COMPLETED);
        appeal.setCompletedAt(OffsetDateTime.now());

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

    private void validateRequestedQuestionScoreUpdates(Appeal appeal, ReviewAppealRequest request) {
        Submission submission = appeal.getSubmission();
        if (submission == null || submission.getSubmissionId() == null) {
            return;
        }

        Map<String, BigDecimal> requestedScores = request.getNewQuestionScores();
        if (requestedScores == null || requestedScores.isEmpty()) {
            return;
        }

        List<Answer> answers = answerRepository
                .findBySubmission_SubmissionIdOrderByQuestion_QuestionNumberAsc(submission.getSubmissionId());

        Map<String, Answer> answerByQuestionKey = new LinkedHashMap<>();
        for (Answer answer : answers) {
            if (answer == null || answer.getQuestion() == null) {
                continue;
            }
            String questionKey = "q" + answer.getQuestion().getQuestionNumber();
            answerByQuestionKey.put(questionKey.toLowerCase(), answer);
        }

        for (Map.Entry<String, BigDecimal> entry : requestedScores.entrySet()) {
            String rawKey = entry.getKey();
            if (rawKey == null || rawKey.isBlank()) {
                continue;
            }

            Answer answer = answerByQuestionKey.get(rawKey.trim().toLowerCase());
            if (answer == null) {
                continue;
            }

            boolean hasJar = answer.getJarFilePath() != null && !answer.getJarFilePath().isBlank();
            boolean hasSource = answer.getSourceCodePath() != null && !answer.getSourceCodePath().isBlank();
            if (hasJar || hasSource) {
                continue;
            }

            BigDecimal requestedScore = entry.getValue();
            BigDecimal originalScore = answer.getAnswerScore() != null ? answer.getAnswerScore() : BigDecimal.ZERO;
            if (requestedScore == null || requestedScore.compareTo(originalScore) == 0) {
                continue;
            }

            int questionNumber = answer.getQuestion().getQuestionNumber();
            throw new IllegalStateException(
                    String.format(
                            "Không thể điều chỉnh điểm cho câu %d vì hệ thống không tìm thấy bài làm của sinh viên ở câu này. Vui lòng giữ nguyên điểm hiện tại và kiểm tra lại dữ liệu nộp bài trước khi gửi kết quả phúc khảo.",
                            questionNumber
                    )
            );
        }
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
                .newQuestionScores(a.getNewQuestionScores())
                .assignedLecturerId(a.getAssignedLecturer() != null ? a.getAssignedLecturer().getUserId() : null)
                .assignedLecturerName(a.getAssignedLecturer() != null ? a.getAssignedLecturer().getFullName() : null)
                .assignedLecturerEmail(a.getAssignedLecturer() != null ? a.getAssignedLecturer().getEmail() : null)
                .createdAt(a.getCreatedAt())
                .assignedAt(a.getAssignedAt())
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
        GradingResultResponse gradingDetail = null;

        if (gradingResult != null) {
            originalScore = gradingResult.getTotalScore();
            testCaseScore = gradingResult.getTestCaseScore();
            oopScore = gradingResult.getOopScore();
            gradingDetail = gradingQueryService.getSubmissionResultDetail(submissionId);
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
                .gradingDetail(gradingDetail)
                .createdAt(a.getCreatedAt())
                .assignedAt(a.getAssignedAt())
                .deadlineAt(a.getDeadlineAt())
                .completedAt(a.getCompletedAt())
                .updatedAt(a.getUpdatedAt())
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
