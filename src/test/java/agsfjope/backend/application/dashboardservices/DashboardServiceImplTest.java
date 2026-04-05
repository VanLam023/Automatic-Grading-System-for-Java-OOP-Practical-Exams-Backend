package agsfjope.backend.application.dashboardservices;

import agsfjope.backend.application.dtos.responses.dashboard.DashboardOverviewResponse;
import agsfjope.backend.application.dtos.responses.dashboard.RecentActivityResponse;
import agsfjope.backend.application.dtos.responses.dashboard.SystemActivityResponse;
import agsfjope.backend.application.dtos.responses.dashboard.SystemHealthResponse;
import agsfjope.backend.application.dtos.responses.dashboard.UserStatsByRoleResponse;
import agsfjope.backend.core.entities.AuditLog;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.enums.ExamStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.auditlog.AuditLogRepository;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.exam.ExamRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import agsfjope.backend.testutils.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests cho DashboardServiceImpl — 17 test cases (N/A/B).
 * Pattern: AAA (Arrange - Act - Assert)
 * Tên method: methodName_Condition_ExpectedBehavior
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock private UserRepository       userRepository;
    @Mock private ExamRepository       examRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private AppealRepository     appealRepository;
    @Mock private AuditLogRepository   auditLogRepository;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    // =========================================================================
    // getOverview()  — [N] 2 normal, [B] 1 boundary
    // =========================================================================

    @Test
    @DisplayName("[N] getOverview - Không có date filter → gọi các count không filtered")
    void getOverview_NoFilter_ReturnsAggregatedCounts() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(userRepository.countByDeletedAtIsNull()).thenReturn(50L);
        when(examRepository.countByStatusAndDeletedAtIsNull(ExamStatus.ONGOING)).thenReturn(3L);
        when(submissionRepository.count()).thenReturn(200L);
        when(appealRepository.countByStatus(AppealStatus.PENDING.name())).thenReturn(5L);

        // ── Act ───────────────────────────────────────────────────────────────
        DashboardOverviewResponse response = dashboardService.getOverview(null, null);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals(50L, response.getTotalUsers());
        assertEquals(3L, response.getActiveExams());
        assertEquals(200L, response.getTotalSubmissions());
        assertEquals(5L, response.getPendingAppeals());
        // Xác nhận repository KHÔNG filtered được gọi
        verify(userRepository, never()).countByDeletedAtIsNullAndCreatedAtBetween(any(), any());
        verify(submissionRepository, never()).countBySubmittedAtBetween(any(), any());
    }

    @Test
    @DisplayName("[N] getOverview - Có date filter → gọi các count filtered theo khoảng thời gian")
    void getOverview_WithDateFilter_ReturnsFilteredCounts() {
        // ── Arrange ──────────────────────────────────────────────────────────
        OffsetDateTime from = OffsetDateTime.now().minusDays(7);
        OffsetDateTime to   = OffsetDateTime.now();

        when(userRepository.countByDeletedAtIsNullAndCreatedAtBetween(from, to)).thenReturn(10L);
        when(examRepository.countByStatusAndDeletedAtIsNullAndCreatedAtBetween(ExamStatus.ONGOING, from, to)).thenReturn(1L);
        when(submissionRepository.countBySubmittedAtBetween(from, to)).thenReturn(45L);
        when(appealRepository.countByStatusAndCreatedAtBetween(AppealStatus.PENDING.name(), from, to)).thenReturn(2L);

        // ── Act ───────────────────────────────────────────────────────────────
        DashboardOverviewResponse response = dashboardService.getOverview(from, to);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals(10L, response.getTotalUsers());
        assertEquals(1L, response.getActiveExams());
        assertEquals(45L, response.getTotalSubmissions());
        assertEquals(2L, response.getPendingAppeals());
        // Xác nhận repository không-filtered KHÔNG được gọi
        verify(userRepository, never()).countByDeletedAtIsNull();
        verify(submissionRepository, never()).count();
    }

    @Test
    @DisplayName("[B] getOverview - from không null nhưng to = null → fallback sang không filtered (Boundary)")
    void getOverview_OnlyFromSet_FallsBackToUnfiltered() {
        // ── Arrange ──────────────────────────────────────────────────────────
        // filtered = from != null && to != null → false khi to = null
        when(userRepository.countByDeletedAtIsNull()).thenReturn(20L);
        when(examRepository.countByStatusAndDeletedAtIsNull(ExamStatus.ONGOING)).thenReturn(0L);
        when(submissionRepository.count()).thenReturn(80L);
        when(appealRepository.countByStatus(AppealStatus.PENDING.name())).thenReturn(0L);

        // ── Act ───────────────────────────────────────────────────────────────
        DashboardOverviewResponse response = dashboardService.getOverview(OffsetDateTime.now().minusDays(1), null);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals(20L, response.getTotalUsers());
        // Phải dùng unfiltered queries
        verify(userRepository).countByDeletedAtIsNull();
        verify(userRepository, never()).countByDeletedAtIsNullAndCreatedAtBetween(any(), any());
    }

    // =========================================================================
    // getUserStatsByRole()  — [N] 2 normal, [B] 1 boundary
    // =========================================================================

    @Test
    @DisplayName("[N] getUserStatsByRole - Không filter → đếm theo role không filtered")
    void getUserStatsByRole_NoFilter_ReturnsRoleBreakdown() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(userRepository.countByDeletedAtIsNull()).thenReturn(100L);
        when(userRepository.countByRole_NameAndDeletedAtIsNull("STUDENT")).thenReturn(60L);
        when(userRepository.countByRole_NameAndDeletedAtIsNull("LECTURER")).thenReturn(20L);
        when(userRepository.countByRole_NameAndDeletedAtIsNull("EXAM_STAFF")).thenReturn(15L);
        when(userRepository.countByRole_NameAndDeletedAtIsNull("SYSTEM_ADMIN")).thenReturn(5L);

        // ── Act ───────────────────────────────────────────────────────────────
        UserStatsByRoleResponse response = dashboardService.getUserStatsByRole(null, null);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals(100L, response.getTotalUsers());
        assertEquals(4, response.getRoles().size());
        assertEquals(60L, response.getRoles().get(0).getCount());   // STUDENT
        assertEquals(20L, response.getRoles().get(1).getCount());   // LECTURER
        assertEquals("Students",  response.getRoles().get(0).getDisplayName());
        assertEquals("Lecturers", response.getRoles().get(1).getDisplayName());
        assertEquals("Staff",     response.getRoles().get(2).getDisplayName());
        assertEquals("Admin",     response.getRoles().get(3).getDisplayName());
    }

    @Test
    @DisplayName("[N] getUserStatsByRole - Có date filter → đếm theo role trong khoảng thời gian")
    void getUserStatsByRole_WithDateFilter_ReturnsFilteredRoleCounts() {
        // ── Arrange ──────────────────────────────────────────────────────────
        OffsetDateTime from = OffsetDateTime.now().minusDays(30);
        OffsetDateTime to   = OffsetDateTime.now();

        when(userRepository.countByDeletedAtIsNullAndCreatedAtBetween(from, to)).thenReturn(15L);
        when(userRepository.countByRole_NameAndDeletedAtIsNullAndCreatedAtBetween("STUDENT",      from, to)).thenReturn(10L);
        when(userRepository.countByRole_NameAndDeletedAtIsNullAndCreatedAtBetween("LECTURER",     from, to)).thenReturn(3L);
        when(userRepository.countByRole_NameAndDeletedAtIsNullAndCreatedAtBetween("EXAM_STAFF",   from, to)).thenReturn(2L);
        when(userRepository.countByRole_NameAndDeletedAtIsNullAndCreatedAtBetween("SYSTEM_ADMIN", from, to)).thenReturn(0L);

        // ── Act ───────────────────────────────────────────────────────────────
        UserStatsByRoleResponse response = dashboardService.getUserStatsByRole(from, to);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals(15L, response.getTotalUsers());
        assertEquals(10L, response.getRoles().get(0).getCount());
        assertEquals(0L,  response.getRoles().get(3).getCount()); // SYSTEM_ADMIN
        verify(userRepository, never()).countByRole_NameAndDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("[B] getUserStatsByRole - Tất cả count trả về 0 (Boundary: không có user nào)")
    void getUserStatsByRole_AllCountsZero_ReturnsZeroRoles() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(userRepository.countByDeletedAtIsNull()).thenReturn(0L);
        when(userRepository.countByRole_NameAndDeletedAtIsNull(any())).thenReturn(0L);

        // ── Act ───────────────────────────────────────────────────────────────
        UserStatsByRoleResponse response = dashboardService.getUserStatsByRole(null, null);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals(0L, response.getTotalUsers());
        assertTrue(response.getRoles().stream().allMatch(r -> r.getCount() == 0));
    }

    // =========================================================================
    // getRecentActivities()  — [N] 2 normal, [A] 1 abnormal
    // =========================================================================

    @Test
    @DisplayName("[N] getRecentActivities - Không filter, có log với user → map đúng username và fullName")
    void getRecentActivities_NoFilter_WithUserLog_ReturnsMappedResponse() {
        // ── Arrange ──────────────────────────────────────────────────────────
        User user    = TestDataFactory.createActiveStudent();
        AuditLog log = TestDataFactory.createAuditLogWithUser(user);
        when(auditLogRepository.findTop10ByOrderByCreatedAtDesc()).thenReturn(List.of(log));

        // ── Act ───────────────────────────────────────────────────────────────
        List<RecentActivityResponse> result = dashboardService.getRecentActivities(10, null, null);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(result);
        assertEquals(1, result.size());
        RecentActivityResponse activity = result.get(0);
        assertEquals("lamtvse173173", activity.getUsername());
        assertEquals("Lam Tran Van",  activity.getFullName());
        assertEquals("LOGIN",         activity.getAction());
        assertEquals("User",          activity.getEntityType());
        assertEquals("192.168.1.10",  activity.getIpAddress());
        verify(auditLogRepository, never()).findTop10ByCreatedAtBetweenOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("[N] getRecentActivities - Log hệ thống (user = null) → username='system', fullName='System'")
    void getRecentActivities_SystemLog_ReturnsSystemAsUsernameAndFullName() {
        // ── Arrange ──────────────────────────────────────────────────────────
        AuditLog systemLog = TestDataFactory.createSystemAuditLog();
        when(auditLogRepository.findTop10ByOrderByCreatedAtDesc()).thenReturn(List.of(systemLog));

        // ── Act ───────────────────────────────────────────────────────────────
        List<RecentActivityResponse> result = dashboardService.getRecentActivities(10, null, null);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("system", result.get(0).getUsername());
        assertEquals("System", result.get(0).getFullName());
        assertEquals("CONFIG_CHANGE", result.get(0).getAction());
    }

    @Test
    @DisplayName("[A] getRecentActivities - Có date filter → gọi findTop10ByCreatedAtBetween")
    void getRecentActivities_WithDateFilter_CallsFilteredRepository() {
        // ── Arrange ──────────────────────────────────────────────────────────
        OffsetDateTime from = OffsetDateTime.now().minusDays(1);
        OffsetDateTime to   = OffsetDateTime.now();
        when(auditLogRepository.findTop10ByCreatedAtBetweenOrderByCreatedAtDesc(from, to))
                .thenReturn(Collections.emptyList());

        // ── Act ───────────────────────────────────────────────────────────────
        List<RecentActivityResponse> result = dashboardService.getRecentActivities(10, from, to);

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(auditLogRepository).findTop10ByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
        verify(auditLogRepository, never()).findTop10ByOrderByCreatedAtDesc();
    }

    // =========================================================================
    // getSystemHealth()  — [N] 1 normal (JVM stats từ runtime thật)
    // =========================================================================

    @Test
    @DisplayName("[N] getSystemHealth - Lấy thông số CPU/Memory/Disk từ JVM thực tế → không throw, trả về số hợp lệ")
    void getSystemHealth_AlwaysReturnsValidMetrics() {
        // ── Arrange ──────────────────────────────────────────────────────────
        // Không cần mock — method sử dụng ManagementFactory và Runtime trực tiếp.

        // ── Act ───────────────────────────────────────────────────────────────
        SystemHealthResponse response = assertDoesNotThrow(() -> dashboardService.getSystemHealth());

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertTrue(response.getCpuUsagePercent() >= 0.0,
                "CPU usage phải >= 0");
        assertTrue(response.getMemoryUsagePercent() >= 0.0 && response.getMemoryUsagePercent() <= 100.0,
                "Memory usage phải trong khoảng [0, 100]");
        assertTrue(response.getDiskUsagePercent() >= 0.0 && response.getDiskUsagePercent() <= 100.0,
                "Disk usage phải trong khoảng [0, 100]");
        assertTrue(response.getTotalMemoryMb() > 0, "Total memory phải > 0");
        assertTrue(response.getUsedMemoryMb() >= 0, "Used memory phải >= 0");
    }

    // =========================================================================
    // getSystemActivity()  — [N] 2 normal ("24h" và "7d"), [B] 1 boundary
    // =========================================================================

    @Test
    @DisplayName("[N] getSystemActivity - period='24h' → sinh 24 bucket giờ, gọi countByCreatedAtAfter")
    void getSystemActivity_24h_Returns24HourlyBuckets() {
        // ── Arrange ──────────────────────────────────────────────────────────
        // Mỗi bucket tính: countAfter(hourStart) - countAfter(hourEnd) = 5 - 3 = 2
        when(auditLogRepository.countByCreatedAtAfter(any(OffsetDateTime.class)))
                .thenReturn(5L, 3L,  5L, 3L,  5L, 3L,  5L, 3L,  5L, 3L,
                            5L, 3L,  5L, 3L,  5L, 3L,  5L, 3L,  5L, 3L,
                            5L, 3L,  5L, 3L,  5L, 3L,  5L, 3L,  5L, 3L,
                            5L, 3L,  5L, 3L,  5L, 3L,  5L, 3L,  5L, 3L,
                            5L, 3L,  5L, 3L,  5L, 3L,  5L, 3L); // 24*2 = 48 lần

        // ── Act ───────────────────────────────────────────────────────────────
        SystemActivityResponse response = dashboardService.getSystemActivity("24h");

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals("24h", response.getPeriod());
        assertEquals(24, response.getDataPoints().size());
        // Mỗi điểm có count = 5-3 = 2, label dạng "HH:00"
        response.getDataPoints().forEach(p -> {
            assertEquals(2L, p.getCount());
            assertTrue(p.getLabel().matches("\\d{2}:00"), "Label phải dạng 'HH:00'");
        });
    }

    @Test
    @DisplayName("[N] getSystemActivity - period='7d' → sinh 7 bucket ngày, gọi countByCreatedAtAfter")
    void getSystemActivity_7d_Returns7DailyBuckets() {
        // ── Arrange ──────────────────────────────────────────────────────────
        // countAfter(dayStart) - countAfter(dayEnd) = 10 - 6 = 4 mỗi ngày
        when(auditLogRepository.countByCreatedAtAfter(any(OffsetDateTime.class)))
                .thenReturn(10L, 6L,  10L, 6L,  10L, 6L,  10L, 6L,
                            10L, 6L,  10L, 6L,  10L, 6L); // 7*2 = 14 lần

        // ── Act ───────────────────────────────────────────────────────────────
        SystemActivityResponse response = dashboardService.getSystemActivity("7d");

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals("7d", response.getPeriod());
        assertEquals(7, response.getDataPoints().size());
        response.getDataPoints().forEach(p -> {
            assertEquals(4L, p.getCount());
            assertTrue(p.getLabel().matches("\\d{2}/\\d{2}"), "Label phải dạng 'dd/MM'");
        });
    }

    @Test
    @DisplayName("[B] getSystemActivity - period='7D' (uppercase, viết hoa) → normalize về '7d'")
    void getSystemActivity_PeriodUpperCase7D_TreatedAs7d() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(auditLogRepository.countByCreatedAtAfter(any(OffsetDateTime.class)))
                .thenReturn(10L, 6L,  10L, 6L,  10L, 6L,  10L, 6L,
                            10L, 6L,  10L, 6L,  10L, 6L);

        // ── Act ───────────────────────────────────────────────────────────────
        SystemActivityResponse response = dashboardService.getSystemActivity("7D");

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals("7d", response.getPeriod()); // equalsIgnoreCase → kết quả "7d"
        assertEquals(7, response.getDataPoints().size());
    }

    @Test
    @DisplayName("[A] getSystemActivity - period=null hoặc giá trị bất kỳ → fallback về 24h")
    void getSystemActivity_NullOrUnknownPeriod_FallsBackTo24h() {
        // ── Arrange ──────────────────────────────────────────────────────────
        when(auditLogRepository.countByCreatedAtAfter(any(OffsetDateTime.class)))
                .thenReturn(0L); // tất cả bucket count = 0

        // ── Act ───────────────────────────────────────────────────────────────
        SystemActivityResponse response = dashboardService.getSystemActivity("unknown_period");

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals("24h", response.getPeriod()); // else branch → "24h"
        assertEquals(24, response.getDataPoints().size());
    }

    @Test
    @DisplayName("[A] getSystemActivity - repository trả về âm (count nhỏ hơn 0) → clamp về 0")
    void getSystemActivity_NegativeCountFromRepo_ClampedToZero() {
        // ── Arrange ──────────────────────────────────────────────────────────
        // hourStart count nhỏ hơn hourEnd count → tổng âm → should clamp to 0
        when(auditLogRepository.countByCreatedAtAfter(any(OffsetDateTime.class)))
                .thenReturn(3L, 5L,  // bucket 1: 3-5 = -2 → clamp 0
                            3L, 5L,  3L, 5L,  3L, 5L,  3L, 5L,
                            3L, 5L,  3L, 5L,  3L, 5L,  3L, 5L,  3L, 5L,
                            3L, 5L,  3L, 5L,  3L, 5L,  3L, 5L,  3L, 5L,
                            3L, 5L,  3L, 5L,  3L, 5L,  3L, 5L,  3L, 5L,
                            3L, 5L,  3L, 5L,  3L, 5L,  3L, 5L);

        // ── Act ───────────────────────────────────────────────────────────────
        SystemActivityResponse response = dashboardService.getSystemActivity("24h");

        // ── Assert ────────────────────────────────────────────────────────────
        assertNotNull(response);
        // Mọi bucket có count âm phải được clamp thành 0
        response.getDataPoints().forEach(p ->
                assertTrue(p.getCount() >= 0, "count phải >= 0 sau khi clamp"));
    }
}
