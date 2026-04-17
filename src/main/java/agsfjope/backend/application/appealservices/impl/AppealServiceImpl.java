package agsfjope.backend.application.appealservices.impl;

import agsfjope.backend.application.appealservices.AppealService;
import agsfjope.backend.application.dtos.requests.appeal.CreateAppealRequest;
import agsfjope.backend.application.dtos.responses.appeal.CreateAppealResponse;
import agsfjope.backend.application.dtos.responses.appeal.MyAppealItemResponse;
import agsfjope.backend.application.dtos.responses.appeal.MyAppealsPageResponse;
import agsfjope.backend.application.walletservices.WalletService;
import agsfjope.backend.core.entities.Appeal;
import agsfjope.backend.core.entities.Submission;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.entities.Wallet;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.enums.SubmissionStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.core.repositories.grading.GradingResultRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import agsfjope.backend.infrastructure.audit.Auditable;
import agsfjope.backend.core.enums.AuditAction;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Implementation của {@link AppealService}.
 *
 * <p>Xử lý toàn bộ luồng tạo đơn phúc khảo:
 * validate → tạo Appeal → tạo Payment → gọi PayOS → trả về QR/checkout URL.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppealServiceImpl implements AppealService {

    // Keys đọc từ SystemConfigs
    private static final String KEY_APPEAL_FEE              = "APPEAL_FEE";

    private static final BigDecimal DEFAULT_FEE = new BigDecimal("200000");

    private final AppealRepository        appealRepository;
    private final SubmissionRepository    submissionRepository;
    private final UserRepository          userRepository;
    private final GradingResultRepository gradingResultRepository;
    private final SystemConfigRepository  systemConfigRepository;
    private final WalletService           walletService;
    private final agsfjope.backend.application.notificationservices.NotificationService notificationService;

    /**
     * Tạo đơn phúc khảo mới.
     *
     * <p>Business rules được kiểm tra:</p>
     * <ul>
     *   <li>BR-01: mỗi submission chỉ có 1 appeal</li>
     *   <li>BR-02: submission phải thuộc về sinh viên đang đăng nhập</li>
     *   <li>BR-03: submission phải có status {@code GRADED}</li>
     * </ul>
     *
     * @param studentId UUID của sinh viên (từ JWT principal)
     * @param request   body yêu cầu tạo appeal
     * @return thông tin appeal + link thanh toán PayOS
     */
    @Override
    @Transactional
    @Auditable(action = AuditAction.CREATE, entityType = "APPEAL")
    public CreateAppealResponse createAppeal(UUID studentId, CreateAppealRequest request) {
        log.info("[Appeal] Student {} đang tạo appeal cho submission {}",
                studentId, request.getSubmissionId());

        // 1. Load Student
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sinh viên: " + studentId));

        // 2. Load Submission
        Submission submission = submissionRepository
                .findById(request.getSubmissionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy bài nộp: " + request.getSubmissionId()));

        // 3. BR-02: Submission phải thuộc về sinh viên đang đăng nhập
        if (!submission.getStudent().getUserId().equals(studentId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bài nộp này không thuộc về bạn");
        }

        // 4. BR-03: Submission phải được chấm xong (GRADED)
        if (submission.getStatus() != SubmissionStatus.GRADED) {
            throw new IllegalStateException(
                    "Bài nộp chưa được chấm điểm. Vui lòng đợi kết quả trước khi phúc khảo.");
        }

        // 5. BR-01: Mỗi submission chỉ có 1 appeal
        if (appealRepository.existsBySubmission_SubmissionId(request.getSubmissionId())) {
            throw new IllegalStateException(
                    "Đã tồn tại đơn phúc khảo cho bài nộp này");
        }

        // 6. Đọc phí phúc khảo từ SystemConfigs (default 200,000 VND)
        BigDecimal fee = loadFee();

        // 7. Lấy tên bài thi từ Submission → Block → Exam
        String examName = submission.getBlock().getExam().getName();

        // 8. Lấy điểm gốc từ GradingResult
        BigDecimal originalScore = gradingResultRepository
                .findBySubmission_SubmissionId(request.getSubmissionId())
                .map(gr -> gr.getTotalScore())
                .orElse(BigDecimal.ZERO);

        // 9. Tạo Appeal với status PENDING_PAYMENT tạm thời
        Appeal appeal = Appeal.builder()
                .submission(submission)
                .student(student)
                .status(AppealStatus.PENDING_PAYMENT)
                .reason(request.getReason())
                .build();
        appeal = appealRepository.save(appeal);
        log.info("[Appeal] Tạo appeal tạm thời, appealId={}", appeal.getAppealId());

        // 10. Trừ tiền từ ví — nếu không đủ sẽ throw và rollback toàn bộ transaction
        Wallet wallet = walletService.debitWalletForAppeal(studentId, fee, appeal.getAppealId());

        // 11. Cập nhật Appeal → PENDING (đã thanh toán bằng ví)
        appealRepository.updateStatus(appeal.getAppealId(), AppealStatus.PENDING);
        log.info("[Appeal] Appeal {} chuyển sang PENDING sau khi trừ ví thành công", appeal.getAppealId());

        // 12. Gửi thông báo cho sinh viên là đã tạo thành công
        notificationService.createNotification(
                studentId,
                "✅ Đơn phúc khảo tạo thành công",
                String.format("Đơn phúc khảo bài thi %s của bạn đã được tiếp nhận và thanh toán phí %s VNĐ.", examName, fee),
                "APPEAL",
                appeal.getAppealId()
        );

        // 13. Thông báo cho Staff (Tìm tất cả user có role EXAM_STAFF hoặc SYSTEM_ADMIN)
        List<User> staffs = userRepository.findByRole_NameAndDeletedAtIsNull("EXAM_STAFF");
        if (staffs.isEmpty()) {
            staffs = userRepository.findByRole_NameAndDeletedAtIsNull("SYSTEM_ADMIN");
        }
        for (User staff : staffs) {
            notificationService.createNotification(
                    staff.getUserId(),
                    "Đơn phúc khảo mới chưa xử lý",
                    String.format("Sinh viên %s vừa gửi một đơn phúc khảo cho kỳ thi %s. Vui lòng kiểm tra và phân công giảng viên.",
                            student.getFullName(), examName),
                    "APPEAL",
                    appeal.getAppealId()
            );
        }

        // 14. Build response
        return CreateAppealResponse.builder()
                .appealId(appeal.getAppealId())
                .submissionId(submission.getSubmissionId())
                .examName(examName)
                .originalScore(originalScore)
                .amount(fee)
                .walletBalanceAfter(wallet.getBalance())
                .build();
    }

    /**
     * Lấy trang "Phúc khảo của tôi" cho sinh viên.
     * Bao gồm overview stats và danh sách chi tiết từng đơn.
     */
    @Override
    @Transactional(readOnly = true)
    public MyAppealsPageResponse getMyAppeals(UUID studentId) {
        log.info("[Appeal] Student {} lấy danh sách appeal", studentId);

        // 1. Count stats
        long pendingCount    = appealRepository.countByStudentAndStatus(studentId, "PENDING");
        long processingCount = appealRepository.countByStudentAndStatus(studentId, "PROCESSING");
        long completedCount  = appealRepository.countByStudentAndStatus(studentId, "COMPLETED");
        long approvedCount   = appealRepository.countByStudentAndStatus(studentId, "APPROVED");
        long deniedCount     = appealRepository.countByStudentAndStatus(studentId, "DENIED");

        // 2. Load all appeals
        List<Appeal> appeals = appealRepository.findByStudentOrderByCreatedAtDesc(studentId);

        // 3. Map to response
        List<MyAppealItemResponse> items = appeals.stream().map(a -> {
            // Lấy điểm gốc
            BigDecimal originalScore = gradingResultRepository
                    .findBySubmission_SubmissionId(a.getSubmission().getSubmissionId())
                    .map(gr -> gr.getTotalScore())
                    .orElse(BigDecimal.ZERO);

            // Lấy tên bài thi
            String examName = "";
            String semester = "";
            try {
                examName = a.getSubmission().getBlock().getExam().getName();
                semester = a.getSubmission().getBlock().getExam().getSemester();
            } catch (Exception e) {
                log.warn("[Appeal] Không thể lấy examName cho appeal {}", a.getAppealId());
            }

            // Tạo mã yêu cầu: #PK-<năm>-<4 ký tự cuối UUID>
            String appealCode = "#PK-" + a.getCreatedAt().getYear()
                    + "-" + a.getAppealId().toString().substring(0, 4).toUpperCase();

            return MyAppealItemResponse.builder()
                    .appealId(a.getAppealId())
                    .submissionId(a.getSubmission() != null ? a.getSubmission().getSubmissionId() : null)
                    .appealCode(appealCode)
                    .examName(examName)
                    .semester(semester)
                    .status(a.getStatus())
                    .originalScore(originalScore)
                    .newScore(a.getNewScore())
                    .newQuestionScores(a.getNewQuestionScores())
                    .reason(a.getReason())
                    .lecturerComment(a.getLecturerComment())
                    .assignedLecturerName(
                            a.getAssignedLecturer() != null
                                    ? a.getAssignedLecturer().getFullName()
                                    : null)
                    .createdAt(a.getCreatedAt())
                    .completedAt(a.getCompletedAt())
                    .deadlineAt(a.getDeadlineAt())
                    .build();
        }).toList();

        return MyAppealsPageResponse.builder()
                .totalAppeals(appeals.size())
                .processingCount(pendingCount + processingCount + completedCount)
                .approvedCount(approvedCount)
                .deniedCount(deniedCount)
                .appeals(items)
                .build();
    }

    // Private Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Đọc phí phúc khảo từ SystemConfigs.
     * Nếu chưa config hoặc lỗi parse → dùng default 200,000 VND.
     */
    private BigDecimal loadFee() {
        return systemConfigRepository.findByConfigKey(KEY_APPEAL_FEE)
                .map(c -> {
                    try {
                        return new BigDecimal(c.getConfigValue());
                    } catch (NumberFormatException e) {
                        log.warn("[Appeal] Config {} không hợp lệ: {}, dùng default", KEY_APPEAL_FEE, c.getConfigValue());
                        return DEFAULT_FEE;
                    }
                })
                .orElseGet(() -> {
                    log.warn("[Appeal] Config {} chưa được cài đặt, dùng default 200,000 VND", KEY_APPEAL_FEE);
                    return DEFAULT_FEE;
                });
    }

    /**
     * Xóa bỏ loadTimeoutMinutes và loadConfig vì không còn dùng PayOS trong createAppeal.
     * WalletService tự quản lý timeout riêng khi xử lý deposit.
     */

}
