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
import agsfjope.backend.configuration.storage.MinioConfig;
import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.entities.GradingResult;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.enums.GradingResultStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.appeal.projections.LecturerAppealWorkloadProjection;
import agsfjope.backend.core.repositories.appeal.projections.StaffAppealListRowProjection;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.payment.PaymentRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final BigDecimal DEFAULT_APPEAL_FEE = new BigDecimal("200000");

    private final AppealRepository appealRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final GradingResultRepository gradingResultRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final MinioService minioService;
    private final MinioConfig minioConfig;
    private final WalletService walletService;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public StaffAppealPageResponse getAppeals(String status, String keyword, String semester, String examName, int page,
                                              int size) {
        log.info("[Staff] Lấy danh sách appeals: status={}, keyword={}, semester={}, examName={}, page={}", status,
                keyword, semester, examName, page);

        String statusParam = (status == null || status.isBlank()) ? null : status.toUpperCase();
        String keywordParam = (keyword == null) ? "" : keyword.trim();
        String semesterParam = (semester == null || semester.isBlank()) ? null : semester.trim();
        String examNameParam = (examName == null || examName.isBlank()) ? null : examName.trim();

        StaffAppealOverviewResponse overview = buildOverview();

        Page<StaffAppealListRowProjection> pageResult = appealRepository.searchAppealRowsForStaff(
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

    @Override
    @Transactional(readOnly = true)
    public StaffAppealDetailResponse getAppealDetail(UUID appealId) {
        log.info("[Staff] Lấy chi tiết appeal {}", appealId);
        Appeal appeal = findAppealOrThrow(appealId);
        return toDetailResponse(appeal);
    }

    @Override
    @Transactional
    public StaffAppealDetailResponse assignLecturer(UUID appealId, AssignAppealRequest request, UUID staffId) {
        log.info("[Staff] Phân công appeal {} cho lecturer {}", appealId, request.getLecturerId());

        Appeal appeal = findAppealOrThrow(appealId);

        if (appeal.getStatus() != AppealStatus.PENDING) {
            throw new IllegalStateException(
                    "Chỉ có thể phân công đơn phúc khảo ở trạng thái PENDING. " +
                            "Trạng thái hiện tại: " + appeal.getStatus());
        }

        User lecturer = userRepository.findById(request.getLecturerId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy giảng viên: " + request.getLecturerId()));

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy staff: " + staffId));

        OffsetDateTime deadline = request.getDeadlineAt();
        if (deadline == null) {
            int deadlineDays = loadDeadlineDays();
            deadline = OffsetDateTime.now().plusDays(deadlineDays);
        } else if (deadline.isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Deadline không được chọn trong quá khứ");
        }

        appeal.setAssignedLecturer(lecturer);
        appeal.setAssignedBy(staff);
        appeal.setAssignedAt(OffsetDateTime.now());
        appeal.setStatus(AppealStatus.PROCESSING);
        appeal.setDeadlineAt(deadline);

        Appeal saved = appealRepository.save(appeal);
        log.info("[Staff] Appeal {} đã phân công cho {}, deadline {}", appealId, lecturer.getFullName(), deadline);

        return toDetailResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LecturerOptionResponse> getLecturerOptions() {
        List<User> lecturers = userRepository.findByRole_NameAndDeletedAtIsNull("LECTURER");
        Map<UUID, Long> workloadMap = new HashMap<>();
        for (LecturerAppealWorkloadProjection row : appealRepository.countActiveAppealsGroupedByLecturer()) {
            workloadMap.put(row.getLecturerId(), row.getActiveAppealCount());
        }

        return lecturers.stream().map(u -> LecturerOptionResponse.builder()
                .lecturerId(u.getUserId())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .activeAppealCount(workloadMap.getOrDefault(u.getUserId(), 0L))
                .build()).toList();
    }

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

            BigDecimal finalScore = appeal.getNewScore() != null ? appeal.getNewScore() : gradingResult.getTotalScore();
            gradingResult.setTotalScore(finalScore);

            if (finalScore != null && finalScore.compareTo(new BigDecimal("4.0")) >= 0) {
                gradingResult.setStatus(GradingResultStatus.PASS);
            } else {
                gradingResult.setStatus(GradingResultStatus.FAIL);
            }

            gradingResultRepository.save(gradingResult);
            log.info("[Staff] Đã cập nhật điểm mới = {}", finalScore);

            BigDecimal refundAmount = loadAppealFee();
            walletService.refundToWallet(
                    appeal.getStudent().getUserId(),
                    refundAmount,
                    appeal.getAppealId());
            log.info("[Staff] Hoàn {} VND vào ví student {} cho appeal {}",
                    refundAmount, appeal.getStudent().getUserId(), appealId);

        } else {
            appeal.setStatus(AppealStatus.DENIED);
            log.info("[Staff] Từ chối cập nhật điểm");

            notificationService.createNotification(
                    appeal.getStudent().getUserId(),
                    "Phúc khảo bị từ chối",
                    "Phúc khảo của bạn đã bị từ chối. Phí phúc khảo sẽ không được hoàn lại.",
                    "APPEAL", appeal.getAppealId());
        }

        appeal.setCompletedAt(OffsetDateTime.now());

        Appeal saved = appealRepository.save(appeal);
        return toDetailResponse(saved);
    }

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

    private StaffAppealListItemResponse toListItem(StaffAppealListRowProjection row) {
        String appealCode = "#PK-" + row.getCreatedAt().getYear()
                + "-" + row.getAppealId().toString().substring(0, 4).toUpperCase();

        return StaffAppealListItemResponse.builder()
                .appealId(row.getAppealId())
                .appealCode(appealCode)
                .studentName(row.getStudentName())
                .studentMssv(row.getStudentMssv())
                .examName(row.getExamName())
                .blockName(row.getBlockName())
                .status(AppealStatus.valueOf(row.getStatus()))
                .originalScore(row.getOriginalScore() != null ? row.getOriginalScore() : BigDecimal.ZERO)
                .newScore(row.getNewScore())
                .createdAt(row.getCreatedAt())
                .deadlineAt(row.getDeadlineAt())
                .assignedLecturerName(row.getAssignedLecturerName())
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

        BigDecimal originalScore = gradingResultRepository
                .findBySubmission_SubmissionId(submissionId)
                .map(GradingResult::getTotalScore)
                .orElse(BigDecimal.ZERO);

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
                .studentId(a.getStudent().getUserId())
                .studentName(a.getStudent().getFullName())
                .studentMssv(a.getStudent().getMssv())
                .studentEmail(a.getStudent().getEmail())
                .examName(examName)
                .semester(semester)
                .blockName(blockName)
                .submissionId(submissionId)
                .submissionFileName(submissionFileName)
                .assignedLecturerId(a.getAssignedLecturer() != null ? a.getAssignedLecturer().getUserId() : null)
                .assignedLecturerName(a.getAssignedLecturer() != null ? a.getAssignedLecturer().getFullName() : null)
                .assignedLecturerEmail(a.getAssignedLecturer() != null ? a.getAssignedLecturer().getEmail() : null)
                .assignedAt(a.getAssignedAt())
                .assignedByName(a.getAssignedBy() != null ? a.getAssignedBy().getFullName() : null)
                .paymentAmount(payment != null ? payment.getAmount() : null)
                .paymentStatus(payment != null ? payment.getStatus() : null)
                .paidAt(payment != null ? payment.getPaidAt() : null)
                .build();
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

    private BigDecimal loadAppealFee() {
        return systemConfigRepository.findByConfigKey(KEY_APPEAL_FEE)
                .map(c -> {
                    try {
                        return new BigDecimal(c.getConfigValue());
                    } catch (NumberFormatException e) {
                        return DEFAULT_APPEAL_FEE;
                    }
                })
                .orElse(DEFAULT_APPEAL_FEE);
    }
}