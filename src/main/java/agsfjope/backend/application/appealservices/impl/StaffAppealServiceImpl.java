package agsfjope.backend.application.appealservices.impl;

import agsfjope.backend.application.appealservices.StaffAppealService;
import agsfjope.backend.application.dtos.requests.appeal.AssignAppealRequest;
import agsfjope.backend.application.dtos.requests.appeal.ConfirmAppealRequest;
import agsfjope.backend.application.dtos.responses.appeal.LecturerOptionResponse;
import agsfjope.backend.application.dtos.responses.appeal.StaffAppealDetailResponse;
import agsfjope.backend.application.dtos.responses.appeal.StaffAppealListItemResponse;
import agsfjope.backend.application.dtos.responses.appeal.StaffAppealOverviewResponse;
import agsfjope.backend.application.dtos.responses.appeal.StaffAppealPageResponse;
import agsfjope.backend.application.notificationservices.NotificationService;
import agsfjope.backend.application.walletservices.WalletService;
import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.entities.GradingResult;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.enums.GradingResultStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.payment.PaymentRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.submission.AnswerRepository;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.configuration.storage.MinioConfig;
import agsfjope.backend.application.ports.out.FileStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import agsfjope.backend.infrastructure.audit.Auditable;
import agsfjope.backend.core.enums.AuditAction;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implementation của {@link StaffAppealService}.
 * Xử lý Appeal Management cho Exam Staff: danh sách, chi tiết, phân công.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffAppealServiceImpl implements StaffAppealService {

    private static final String KEY_DEADLINE_DAYS = "APPEAL_DEADLINE_DAYS";
    private static final int DEFAULT_DEADLINE_DAYS = 7;
    private static final String KEY_APPEAL_FEE = "APPEAL_FEE";
    private static final java.math.BigDecimal DEFAULT_APPEAL_FEE = new java.math.BigDecimal("200000");

    private final AppealRepository appealRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final GradingResultRepository gradingResultRepository;
    private final AnswerRepository answerRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final FileStoragePort minioService;
    private final MinioConfig minioConfig;
    private final WalletService walletService;
    private final NotificationService notificationService;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Danh sách + stats
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public StaffAppealPageResponse getAppeals(String status, String keyword, String semester, String examName, int page,
            int size) {
        log.info("[Staff] Lấy danh sách appeals: status={}, keyword={}, semester={}, examName={}, page={}", status,
                keyword, semester, examName, page);

        // Normalize params
        String statusParam = (status == null || status.isBlank()) ? null : status.toUpperCase();
        String keywordParam = (keyword == null) ? "" : keyword.trim();
        String semesterParam = (semester == null || semester.isBlank()) ? null : semester.trim();
        String examNameParam = (examName == null || examName.isBlank()) ? null : examName.trim();

        // 1. Overview stats
        StaffAppealOverviewResponse overview = buildOverview();

        // 2. Paged list
        Page<Appeal> pageResult = appealRepository.searchAppealsForStaff(
                statusParam, keywordParam, semesterParam, examNameParam, PageRequest.of(page, size));

        List<StaffAppealListItemResponse> items = pageResult.getContent()
                .stream()
                .map(this::toListItem)
                .toList();

        return StaffAppealPageResponse.builder()
                .overview(overview)
                .appeals(items)
                .currentPage(pageResult.getNumber())
                .totalPages(pageResult.getTotalPages())
                .totalElements(pageResult.getTotalElements())
                .pageSize(pageResult.getSize())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Chi tiết
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public StaffAppealDetailResponse getAppealDetail(UUID appealId) {
        log.info("[Staff] Lấy chi tiết appeal {}", appealId);
        Appeal appeal = findAppealOrThrow(appealId);
        return toDetailResponse(appeal);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Phân công giảng viên
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @Auditable(action = AuditAction.ASSIGN, entityType = "APPEAL")
    public StaffAppealDetailResponse assignLecturer(UUID appealId, AssignAppealRequest request, UUID staffId) {
        log.info("[Staff] Phân công appeal {} cho lecturer {}", appealId, request.getLecturerId());

        Appeal appeal = findAppealOrThrow(appealId);

        // BR-05: chỉ assign được khi PENDING
        if (appeal.getStatus() != AppealStatus.PENDING) {
            throw new IllegalStateException(
                    "Chỉ có thể phân công đơn phúc khảo ở trạng thái PENDING. " +
                            "Trạng thái hiện tại: " + appeal.getStatus());
        }

        // Load lecturer
        User lecturer = userRepository.findById(request.getLecturerId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy giảng viên: " + request.getLecturerId()));

        // Load staff (người thực hiện phân công)
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy staff: " + staffId));

        // Validation deadline
        OffsetDateTime deadline = request.getDeadlineAt();
        if (deadline == null) {
            int deadlineDays = loadDeadlineDays();
            deadline = OffsetDateTime.now().plusDays(deadlineDays);
        } else if (deadline.isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Deadline không được chọn trong quá khứ");
        }

        // Cập nhật Appeal
        appeal.setAssignedLecturer(lecturer);
        appeal.setAssignedBy(staff);
        appeal.setAssignedAt(OffsetDateTime.now());
        appeal.setStatus(AppealStatus.PROCESSING);
        appeal.setDeadlineAt(deadline);

        Appeal saved = appealRepository.save(appeal);
        log.info("[Staff] Appeal {} đã phân công cho {}, deadline {}", appealId, lecturer.getFullName(), deadline);

        // Notify Lecturer
        notificationService.createNotification(
                lecturer.getUserId(),
                "Phân công chấm phúc khảo mới",
                String.format("Exam Staff đã giao cho bạn chấm một đơn phúc khảo. Hạn chót nộp báo cáo điểm: %s",
                        deadline.toLocalDate().toString()),
                "APPEAL",
                appeal.getAppealId()
        );

        return toDetailResponse(saved);
    }


    @Override
    @Transactional
    public StaffAppealDetailResponse cancelAppeal(UUID appealId, UUID staffId) {
        log.info("[Staff] Hủy appeal {} bởi staff {}", appealId, staffId);

        Appeal appeal = findAppealOrThrow(appealId);

        if (appeal.getStatus() != AppealStatus.PENDING && appeal.getStatus() != AppealStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Chỉ có thể hủy đơn phúc khảo ở trạng thái PENDING hoặc PROCESSING. Trạng thái hiện tại: "
                            + appeal.getStatus());
        }

        appeal.setStatus(AppealStatus.CANCELLED);
        appeal.setCompletedAt(OffsetDateTime.now());

        Appeal saved = appealRepository.save(appeal);

        notificationService.createNotification(
                appeal.getStudent().getUserId(),
                "Đơn phúc khảo đã bị hủy",
                "Đơn phúc khảo của bạn đã được Exam Staff hủy xử lý.",
                "APPEAL",
                appeal.getAppealId());

        return toDetailResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Danh sách giảng viên (dropdown)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<LecturerOptionResponse> getLecturerOptions() {
        List<User> lecturers = userRepository.findByRole_NameAndDeletedAtIsNull("LECTURER");
        return lecturers.stream().map(u -> LecturerOptionResponse.builder()
                .lecturerId(u.getUserId())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .activeAppealCount(appealRepository.countActiveAppealsByLecturer(u.getUserId()))
                .build()).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Xác nhận đơn phúc khảo
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public StaffAppealDetailResponse confirmAppeal(UUID appealId, ConfirmAppealRequest request, UUID staffId) {
        log.info("[Staff] Xác nhận appeal {}. Approve={}", appealId, request.getIsApprove());

        Appeal appeal = findAppealOrThrow(appealId);

        if (appeal.getStatus() != AppealStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Theo quy trình, chỉ có thể phê duyệt đơn phúc khảo khi giảng viên đã chấm (COMPLETED). " +
                            "Trạng thái hiện tại: " + appeal.getStatus());
        }

        if (Boolean.TRUE.equals(request.getIsApprove())) {
            appeal.setStatus(AppealStatus.APPROVED);

            GradingResult gradingResult = gradingResultRepository
                    .findBySubmission_SubmissionId(appeal.getSubmission().getSubmissionId())
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy kết quả chấm của bài thi này"));

            // Cập nhật điểm mới, nếu newScore null (chưa có điểm mới) thì giữ nguyên điểm
            // gốc
            BigDecimal finalScore = appeal.getNewScore() != null ? appeal.getNewScore() : gradingResult.getTotalScore();
            gradingResult.setTotalScore(finalScore);

            // Đánh giá lại PASS/FAIL
            if (finalScore != null && finalScore.compareTo(new BigDecimal("4.0")) >= 0) {
                gradingResult.setStatus(GradingResultStatus.PASS);
            } else {
                gradingResult.setStatus(GradingResultStatus.FAIL);
            }

            gradingResultRepository.save(gradingResult);
            log.info("[Staff] Đã cập nhật điểm mới = {}", finalScore);

            // Hoàn tiền ví cho sinh viên khi APPROVED
            // WalletService tự gửi notification cho student
            java.math.BigDecimal refundAmount = loadAppealFee();
            walletService.refundToWallet(
                    appeal.getStudent().getUserId(),
                    refundAmount,
                    appeal.getAppealId());
            log.info("[Staff] Hoàn {} VND vào ví student {} cho appeal {}",
                    refundAmount, appeal.getStudent().getUserId(), appealId);

        } else {
            appeal.setStatus(AppealStatus.DENIED);
            log.info("[Staff] Từ chối cập nhật điểm");

            // Gửi notification cho student khi DENIED
            notificationService.createNotification(
                    appeal.getStudent().getUserId(),
                    "Phúc khảo bị từ chối",
                    "Phúc khảo của bạn đã bị từ chối. Phí phúc khảo sẽ không được hoàn lại.",
                    "APPEAL", appeal.getAppealId());
        }

        // Cập nhật thời gian hoàn thành toàn bộ
        appeal.setCompletedAt(OffsetDateTime.now());

        Appeal saved = appealRepository.save(appeal);
        return toDetailResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Download Submission
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public InputStream downloadSubmission(UUID appealId) {
        log.info("[Staff] Download submission cho appeal: {}", appealId);
        Appeal appeal = findAppealOrThrow(appealId);

        if (appeal.getSubmission() == null || appeal.getSubmission().getFilePath() == null) {
            throw new IllegalStateException("Không tìm thấy file bài làm đính kèm trong cơ sở dữ liệu");
        }

        return minioService.downloadFile(
                minioConfig.getBucket().getSubmissions(),
                appeal.getSubmission().getFilePath());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Appeal findAppealOrThrow(UUID appealId) {
        return appealRepository.findById(appealId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy đơn phúc khảo: " + appealId));
    }

    private StaffAppealOverviewResponse buildOverview() {
        return StaffAppealOverviewResponse.builder()
                .total(appealRepository.count())
                .pending(appealRepository.countByStatus("PENDING"))
                .processing(appealRepository.countByStatus("PROCESSING"))
                .approved(appealRepository.countByStatus("APPROVED"))
                .denied(appealRepository.countByStatus("DENIED"))
                .cancelled(appealRepository.countByStatus("CANCELLED"))
                .build();
    }

    private StaffAppealListItemResponse toListItem(Appeal a) {
        String examName = "", semester = "", blockName = "";
        UUID submissionId = null;
        try {
            examName = a.getSubmission().getBlock().getExam().getName();
            semester = a.getSubmission().getBlock().getExam().getSemester();
            blockName = a.getSubmission().getBlock().getName();
            submissionId = a.getSubmission().getSubmissionId();
        } catch (Exception ignored) {
        }

        BigDecimal originalScore = getOriginalScore(submissionId);

        String appealCode = "#PK-" + a.getCreatedAt().getYear()
                + "-" + a.getAppealId().toString().substring(0, 4).toUpperCase();

        return StaffAppealListItemResponse.builder()
                .appealId(a.getAppealId())
                .appealCode(appealCode)
                .studentName(a.getStudent().getFullName())
                .studentMssv(a.getStudent().getMssv())
                .examName(examName)
                .semester(semester)
                .blockName(blockName)
                .submissionId(submissionId)
                .status(a.getStatus())
                .originalScore(originalScore)
                .newScore(a.getNewScore())
                .newQuestionScores(a.getNewQuestionScores())
                .createdAt(a.getCreatedAt())
                .deadlineAt(a.getDeadlineAt())
                .assignedLecturerName(
                        a.getAssignedLecturer() != null ? a.getAssignedLecturer().getFullName() : null)
                .build();
    }

    private StaffAppealDetailResponse toDetailResponse(Appeal a) {
        String examName = "", semester = "", blockName = "";
        String submissionFileName = "";
        UUID submissionId = null;
        try {
            examName = a.getSubmission().getBlock().getExam().getName();
            semester = a.getSubmission().getBlock().getExam().getSemester();
            blockName = a.getSubmission().getBlock().getName();
            submissionFileName = a.getSubmission().getFileName();
            submissionId = a.getSubmission().getSubmissionId();
        } catch (Exception ignored) {
        }

        BigDecimal originalScore = getOriginalScore(submissionId);

        // Payment info
        var payment = paymentRepository.findByAppealId(a.getAppealId()).orElse(null);

        String appealCode = "#PK-" + a.getCreatedAt().getYear()
                + "-" + a.getAppealId().toString().substring(0, 4).toUpperCase();

        return StaffAppealDetailResponse.builder()
                .appealId(a.getAppealId())
                .appealCode(appealCode)
                .status(a.getStatus())
                .reason(a.getReason())
                .lecturerComment(a.getLecturerComment())
                .originalScore(originalScore)
                .newScore(a.getNewScore())
                .newQuestionScores(a.getNewQuestionScores())
                .createdAt(a.getCreatedAt())
                .deadlineAt(a.getDeadlineAt())
                .completedAt(a.getCompletedAt())
                // Student
                .studentId(a.getStudent().getUserId())
                .studentName(a.getStudent().getFullName())
                .studentMssv(a.getStudent().getMssv())
                .studentEmail(a.getStudent().getEmail())
                // Exam
                .examName(examName)
                .semester(semester)
                .blockName(blockName)
                // Submission
                .submissionId(submissionId)
                .submissionFileName(submissionFileName)
                // Assigned Lecturer
                .assignedLecturerId(a.getAssignedLecturer() != null ? a.getAssignedLecturer().getUserId() : null)
                .assignedLecturerName(a.getAssignedLecturer() != null ? a.getAssignedLecturer().getFullName() : null)
                .assignedLecturerEmail(a.getAssignedLecturer() != null ? a.getAssignedLecturer().getEmail() : null)
                .assignedAt(a.getAssignedAt())
                .assignedByName(a.getAssignedBy() != null ? a.getAssignedBy().getFullName() : null)
                // Payment
                .paymentAmount(payment != null ? payment.getAmount() : null)
                .paymentStatus(payment != null ? payment.getStatus() : null)
                .paidAt(payment != null ? payment.getPaidAt() : null)
                .build();
    }


    private BigDecimal getOriginalScore(UUID submissionId) {
        if (submissionId == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal sumAnswerScores = answerRepository.findBySubmission_SubmissionIdOrderByQuestion_QuestionNumberAsc(submissionId)
                .stream()
                .map(answer -> answer.getAnswerScore() != null ? answer.getAnswerScore() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sumAnswerScores.compareTo(BigDecimal.ZERO) > 0) {
            return sumAnswerScores;
        }

        return gradingResultRepository.findBySubmission_SubmissionId(submissionId)
                .map(GradingResult::getTotalScore)
                .orElse(BigDecimal.ZERO);
    }

    private int loadDeadlineDays() {
        return systemConfigRepository.findByConfigKey(KEY_DEADLINE_DAYS)
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getConfigValue());
                    } catch (NumberFormatException e) {
                        return DEFAULT_DEADLINE_DAYS;
                    }
                })
                .orElse(DEFAULT_DEADLINE_DAYS);
    }

    /**
     * Đọc phí phúc khảo từ SystemConfigs (dùng khi hoàn tiền).
     */
    private java.math.BigDecimal loadAppealFee() {
        return systemConfigRepository.findByConfigKey(KEY_APPEAL_FEE)
                .map(c -> {
                    try {
                        return new java.math.BigDecimal(c.getConfigValue());
                    } catch (NumberFormatException e) {
                        return DEFAULT_APPEAL_FEE;
                    }
                })
                .orElse(DEFAULT_APPEAL_FEE);
    }
}
