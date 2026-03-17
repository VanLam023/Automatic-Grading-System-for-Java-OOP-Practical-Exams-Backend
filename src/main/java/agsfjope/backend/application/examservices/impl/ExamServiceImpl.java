package agsfjope.backend.application.examservices.impl;

import agsfjope.backend.application.dtos.requests.exam.CreateExamRequest;
import agsfjope.backend.application.dtos.requests.exam.UpdateExamRequest;
import agsfjope.backend.application.dtos.responses.exam.ExamResponse;
import agsfjope.backend.application.examservices.ExamService;
import agsfjope.backend.core.entities.Exam;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.GradingMode;
import agsfjope.backend.core.exceptions.auth.NotFoundException;
import agsfjope.backend.core.exceptions.exam.ExamConflictException;
import agsfjope.backend.core.repositories.config.SystemConfigRepository;
import agsfjope.backend.core.repositories.exam.ExamRepository;
import agsfjope.backend.infrastructure.security.SecurityUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ExamService}.
 *
 * <p>Business rules applied:</p>
 * <ul>
 *   <li>Academic year is auto-calculated from the current date: 2 semesters per year
 *       (Jan–Aug = Year N / Year N-1, Sep–Dec = Year N / Year N+1)</li>
 *   <li>Semester must be unique: only one active exam per semester code.</li>
 *   <li>Start/end time must both fall within the current academic year date range.</li>
 *   <li>Exam duration (start → end) cannot exceed 4 months 15 days (≈135 days).</li>
 *   <li>GradingMode is auto-fetched from system default config (DEFAULT_GRADING_MODE).</li>
 *   <li>Only EXAM_STAFF can call these operations (enforced via @PreAuthorize in controller).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private final ExamRepository examRepository;
    private final SystemConfigRepository systemConfigRepository;

    /** Maximum allowed exam duration: 4 months and 15 days = 135 days */
    private static final int MAX_EXAM_DURATION_DAYS = 135;

    /** System config key for the default grading mode */
    private static final String DEFAULT_GRADING_MODE_KEY = "DEFAULT_GRADING_MODE";

    // ─────────────────────────────────────────────────────────
    // USE CASES
    // ─────────────────────────────────────────────────────────

    /**
     * Create exam flow:
     * 1. Auto-calculate current {@code academicYear} and its valid date range.
     * 2. Validate {@code semester} is unique.
     * 3. Validate {@code startTime} and {@code endTime} fall within academicYear range.
     * 4. Validate exam duration &le; 135 days.
     * 5. Auto-fetch default GradingMode from system config.
     * 6. Save and return.
     *
     * @param request exam creation payload
     * @return created exam response
     */
    @Override
    @Transactional
    public ExamResponse createExam(CreateExamRequest request) {
        User currentUser = SecurityUtils.getCurrentUser();

        // === STEP 1: Auto-calculate academic year and its date range ===
        String academicYear = calculateAcademicYear();
        OffsetDateTime[] yearRange = getAcademicYearRange(academicYear);
        OffsetDateTime yearStart = yearRange[0];
        OffsetDateTime yearEnd   = yearRange[1];

        // === STEP 2: Semester uniqueness ===
        if (examRepository.existsBySemesterAndDeletedAtIsNull(request.getSemester())) {
            throw new IllegalArgumentException(
                    "Học kỳ \"" + request.getSemester() + "\" đã có kỳ thi. Mỗi học kỳ chỉ được tổ chức 1 kỳ thi."
            );
        }

        // === STEP 3: Start/end must be within current academic year ===
        validateWithinAcademicYear(request.getStartTime(), request.getEndTime(), yearStart, yearEnd);

        // === STEP 4: Duration ≤ 135 days ===
        long durationDays = java.time.Duration.between(request.getStartTime(), request.getEndTime()).toDays();
        if (durationDays > MAX_EXAM_DURATION_DAYS) {
            throw new IllegalArgumentException(
                    "Khoảng thời gian kỳ thi không được vượt quá 4 tháng 15 ngày (135 ngày). " +
                    "Hiện tại: " + durationDays + " ngày."
            );
        }

        // === STEP 5: Auto-fetch default grading mode from system config ===
        GradingMode gradingMode = fetchDefaultGradingMode();

        // === STEP 6: Build and save ===
        Exam exam = Exam.builder()
                .name(request.getName())
                .semester(request.getSemester())
                .academicYear(academicYear)
                .description(request.getDescription())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .gradingMode(gradingMode)
                .createdBy(currentUser)
                .build();

        examRepository.save(exam);
        return mapToResponse(exam);
    }

    /**
     * Returns all active (non-deleted) exams.
     *
     * @return list of exam responses
     */
    @Override
    public List<ExamResponse> getAllExams() {
        return examRepository.findAllByDeletedAtIsNull()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get exam by id — only non-deleted exams are returned.
     *
     * @param examId exam identifier
     * @return exam response
     */
    @Override
    public ExamResponse getExamById(UUID examId) {
        Exam exam = examRepository.findByExamIdAndDeletedAtIsNull(examId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy kỳ thi với ID: " + examId));
        return mapToResponse(exam);
    }

    /**
     * Update mutable exam fields.
     * Only non-null fields in the request will overwrite existing values.
     * If semester changes, it must remain unique.
     * If start/end time changes, they must still be within the current academic year.
     *
     * @param examId  exam identifier
     * @param request update payload
     * @return updated exam response
     */
    @Override
    @Transactional
    public ExamResponse updateExam(UUID examId, UpdateExamRequest request) {
        Exam exam = examRepository.findByExamIdAndDeletedAtIsNull(examId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy kỳ thi với ID: " + examId));

        // Validate semester uniqueness if changed
        if (request.getSemester() != null
                && !request.getSemester().equals(exam.getSemester())) {
            if (examRepository.existsBySemesterAndDeletedAtIsNullAndExamIdNot(request.getSemester(), examId)) {
                throw new IllegalArgumentException(
                        "Học kỳ \"" + request.getSemester() + "\" đã có kỳ thi khác. Mỗi học kỳ chỉ được tổ chức 1 kỳ thi."
                );
            }
        }

        // Resolve effective start/end for range validation
        OffsetDateTime effectiveStart = request.getStartTime() != null ? request.getStartTime() : exam.getStartTime();
        OffsetDateTime effectiveEnd   = request.getEndTime()   != null ? request.getEndTime()   : exam.getEndTime();

        // Validate within current academic year
        String academicYear = exam.getAcademicYear();
        OffsetDateTime[] yearRange = getAcademicYearRange(academicYear);
        validateWithinAcademicYear(effectiveStart, effectiveEnd, yearRange[0], yearRange[1]);

        // Validate duration ≤ 135 days
        long durationDays = java.time.Duration.between(effectiveStart, effectiveEnd).toDays();
        if (durationDays > MAX_EXAM_DURATION_DAYS) {
            throw new IllegalArgumentException(
                    "Khoảng thời gian kỳ thi không được vượt quá 4 tháng 15 ngày (135 ngày). " +
                    "Hiện tại: " + durationDays + " ngày."
            );
        }

        // Apply updates
        if (request.getName()      != null) exam.setName(request.getName());
        if (request.getSemester()  != null) exam.setSemester(request.getSemester());
        if (request.getDescription()!= null) exam.setDescription(request.getDescription());
        if (request.getStartTime() != null) exam.setStartTime(request.getStartTime());
        if (request.getEndTime()   != null) exam.setEndTime(request.getEndTime());

        examRepository.save(exam);
        return mapToResponse(exam);
    }

    /**
     * Soft-delete exam by setting deletedAt timestamp.
     * Throws {@link ExamConflictException} if any submission exists for this exam's blocks (BR-12).
     *
     * @param examId exam identifier
     */
    @Override
    @Transactional
    public void deleteExam(UUID examId) {
        Exam exam = examRepository.findByExamIdAndDeletedAtIsNull(examId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy kỳ thi với ID: " + examId));

        // BR-12: Only delete exam if no submissions exist on any block
        if (examRepository.existsSubmissionsByExamId(examId)) {
            throw new ExamConflictException(
                    "Không thể xóa kỳ thi \"" + exam.getName() + "\" vì đã có sinh viên nộp bài. " +
                    "Vui lòng kiểm tra lại trước khi xóa."
            );
        }

        exam.setDeletedAt(OffsetDateTime.now());
        examRepository.save(exam);
    }

    // ─────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────

    /**
     * Auto-calculates the current academic year string based on today's date.
     *
     * <p>Rules (FPT University semester convention):</p>
     * <ul>
     *   <li>Jan–Aug  (SP + SU semesters): academicYear = (currentYear-1)–currentYear, e.g., "2024-2025"</li>
     *   <li>Sep–Dec  (FA semester):       academicYear = currentYear–(currentYear+1), e.g., "2025-2026"</li>
     * </ul>
     *
     * @return academic year string in format "YYYY-YYYY"
     */
    private String calculateAcademicYear() {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        // Sep–Dec: FA semester starts new academic year
        if (today.getMonth().compareTo(Month.SEPTEMBER) >= 0) {
            return year + "-" + (year + 1);
        } else {
            // Jan–Aug: still in the academic year that started previous Sep
            return (year - 1) + "-" + year;
        }
    }

    /**
     * Returns the [start, end] date range of an academic year.
     * Academic year "YYYY-YYYY+1" spans from Sep 1 of YYYY to Aug 31 of YYYY+1.
     *
     * @param academicYear academic year string (e.g. "2024-2025")
     * @return array [startOfYear, endOfYear] as OffsetDateTime in UTC+7
     */
    private OffsetDateTime[] getAcademicYearRange(String academicYear) {
        String[] parts = academicYear.split("-");
        int startYear = Integer.parseInt(parts[0]);
        int endYear   = Integer.parseInt(parts[1]);
        ZoneOffset vn = ZoneOffset.ofHours(7);
        OffsetDateTime start = OffsetDateTime.of(startYear, 9, 1, 0, 0, 0, 0, vn);
        OffsetDateTime end   = OffsetDateTime.of(endYear,   8, 31, 23, 59, 59, 0, vn);
        return new OffsetDateTime[]{start, end};
    }

    /**
     * Validates that both start and end times fall within the given academic year range.
     *
     * @param startTime exam start time
     * @param endTime   exam end time
     * @param yearStart first day of academic year (Sep 1)
     * @param yearEnd   last day of academic year (Aug 31)
     */
    private void validateWithinAcademicYear(
            OffsetDateTime startTime, OffsetDateTime endTime,
            OffsetDateTime yearStart, OffsetDateTime yearEnd
    ) {
        if (startTime.isBefore(yearStart) || startTime.isAfter(yearEnd)) {
            throw new IllegalArgumentException(
                    "Thời gian bắt đầu phải nằm trong năm học hiện tại (" +
                    yearStart.toLocalDate() + " đến " + yearEnd.toLocalDate() + ")."
            );
        }
        if (endTime.isBefore(yearStart) || endTime.isAfter(yearEnd)) {
            throw new IllegalArgumentException(
                    "Thời gian kết thúc phải nằm trong năm học hiện tại (" +
                    yearStart.toLocalDate() + " đến " + yearEnd.toLocalDate() + ")."
            );
        }
    }

    /**
     * Fetches the system-default GradingMode from SystemConfig.
     * Throws {@link IllegalStateException} if the config key is missing or invalid.
     *
     * @return the configured default GradingMode
     */
    private GradingMode fetchDefaultGradingMode() {
        String modeValue = systemConfigRepository.findByConfigKey(DEFAULT_GRADING_MODE_KEY)
                .map(config -> config.getConfigValue())
                .orElseThrow(() -> new IllegalStateException(
                        "Chưa cấu hình chế độ chấm điểm mặc định. " +
                        "Vui lòng liên hệ System Admin để cấu hình tại /api/admin/config/grading-modes/{mode}/set-default."
                ));
        try {
            return GradingMode.valueOf(modeValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Giá trị chế độ chấm điểm mặc định không hợp lệ trong hệ thống: \"" + modeValue + "\". " +
                    "Vui lòng liên hệ System Admin."
            );
        }
    }

    /**
     * Maps Exam entity to ExamResponse DTO including all fields.
     */
    private ExamResponse mapToResponse(Exam exam) {
        return ExamResponse.builder()
                .examId(exam.getExamId())
                .name(exam.getName())
                .semester(exam.getSemester())
                .academicYear(exam.getAcademicYear())
                .description(exam.getDescription())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .status(exam.getStatus())
                .gradingMode(exam.getGradingMode())
                .createdBy(exam.getCreatedBy() != null ? exam.getCreatedBy().getUsername() : null)
                .createdAt(exam.getCreatedAt())
                .build();
    }
}
