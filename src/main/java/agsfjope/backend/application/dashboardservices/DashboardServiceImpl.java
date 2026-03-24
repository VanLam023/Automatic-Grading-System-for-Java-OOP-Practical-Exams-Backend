package agsfjope.backend.application.dashboardservices;

import agsfjope.backend.application.dtos.responses.dashboard.DashboardOverviewResponse;
import agsfjope.backend.application.dtos.responses.dashboard.RecentActivityResponse;
import agsfjope.backend.application.dtos.responses.dashboard.SystemActivityResponse;
import agsfjope.backend.application.dtos.responses.dashboard.SystemActivityResponse.ActivityPoint;
import agsfjope.backend.application.dtos.responses.dashboard.SystemHealthResponse;
import agsfjope.backend.application.dtos.responses.dashboard.UserStatsByRoleResponse;
import agsfjope.backend.application.dtos.responses.dashboard.UserStatsByRoleResponse.RoleCount;
import agsfjope.backend.core.entities.AuditLog;
import agsfjope.backend.core.enums.AppealStatus;
import agsfjope.backend.core.enums.ExamStatus;
import agsfjope.backend.core.repositories.appeal.AppealRepository;
import agsfjope.backend.core.repositories.auditlog.AuditLogRepository;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.core.repositories.exam.ExamRepository;
import agsfjope.backend.core.repositories.submission.SubmissionRepository;
import com.sun.management.OperatingSystemMXBean;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link DashboardService}.
 * <p>
 * Aggregates data from multiple repositories to power the five sections
 * of the Admin Dashboard. All read operations are wrapped in a read-only
 * transaction for consistency.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository       userRepository;
    private final ExamRepository       examRepository;
    private final SubmissionRepository submissionRepository;
    private final AppealRepository     appealRepository;
    private final AuditLogRepository   auditLogRepository;

    // ─── Overview ───────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     */
    @Override
    public DashboardOverviewResponse getOverview(OffsetDateTime from, OffsetDateTime to) {
        boolean filtered = from != null && to != null;

        long totalUsers       = filtered
                ? userRepository.countByDeletedAtIsNullAndCreatedAtBetween(from, to)
                : userRepository.countByDeletedAtIsNull();
        long activeExams      = filtered
                ? examRepository.countByStatusAndDeletedAtIsNullAndCreatedAtBetween(ExamStatus.ONGOING, from, to)
                : examRepository.countByStatusAndDeletedAtIsNull(ExamStatus.ONGOING);
        long totalSubmissions = filtered
                ? submissionRepository.countBySubmittedAtBetween(from, to)
                : submissionRepository.count();
        long pendingAppeals   = filtered
                ? appealRepository.countByStatusAndCreatedAtBetween(AppealStatus.PENDING.name(), from, to)
                : appealRepository.countByStatus(AppealStatus.PENDING.name());

        return DashboardOverviewResponse.builder()
                .totalUsers(totalUsers)
                .activeExams(activeExams)
                .totalSubmissions(totalSubmissions)
                .pendingAppeals(pendingAppeals)
                .build();
    }

    // ─── User Stats by Role ──────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     * <p>
     * Counts users for each of the four application roles:
     * STUDENT, LECTURER, EXAM_STAFF, and SYSTEM_ADMIN.
     * </p>
     */
    @Override
    public UserStatsByRoleResponse getUserStatsByRole(OffsetDateTime from, OffsetDateTime to) {
        boolean filtered = from != null && to != null;

        long totalUsers = filtered
                ? userRepository.countByDeletedAtIsNullAndCreatedAtBetween(from, to)
                : userRepository.countByDeletedAtIsNull();

        List<RoleCount> roles = List.of(
                buildRoleCount("STUDENT",      "Students",  filtered, from, to),
                buildRoleCount("LECTURER",     "Lecturers", filtered, from, to),
                buildRoleCount("EXAM_STAFF",   "Staff",     filtered, from, to),
                buildRoleCount("SYSTEM_ADMIN", "Admin",     filtered, from, to)
        );

        return UserStatsByRoleResponse.builder()
                .totalUsers(totalUsers)
                .roles(roles)
                .build();
    }

    /**
     * Helper to build a single {@link RoleCount} entry, optionally filtered by date range.
     *
     * @param roleName    role name as stored in the database
     * @param displayName human-readable role label for the frontend
     * @param filtered    whether to apply a date range filter
     * @param from        start of date range (used only when filtered is true)
     * @param to          end of date range (used only when filtered is true)
     * @return a populated RoleCount DTO
     */
    private RoleCount buildRoleCount(String roleName, String displayName,
                                     boolean filtered, OffsetDateTime from, OffsetDateTime to) {
        long count = filtered
                ? userRepository.countByRole_NameAndDeletedAtIsNullAndCreatedAtBetween(roleName, from, to)
                : userRepository.countByRole_NameAndDeletedAtIsNull(roleName);
        return RoleCount.builder()
                .roleName(roleName)
                .displayName(displayName)
                .count(count)
                .build();
    }

    // ─── Recent Activities ───────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     * <p>
     * Fetches the top 10 most recent audit log entries and maps each one
     * to a {@link RecentActivityResponse}. The {@code limit} parameter is
     * accepted for API flexibility but the repository is fixed at 10 records
     * to avoid unbounded queries.
     * </p>
     */
    @Override
    public List<RecentActivityResponse> getRecentActivities(int limit, OffsetDateTime from, OffsetDateTime to) {
        boolean filtered = from != null && to != null;
        List<AuditLog> logs = filtered
                ? auditLogRepository.findTop10ByCreatedAtBetweenOrderByCreatedAtDesc(from, to)
                : auditLogRepository.findTop10ByOrderByCreatedAtDesc();

        List<RecentActivityResponse> result = new ArrayList<>();
        for (AuditLog log : logs) {
            String username = log.getUser() != null ? log.getUser().getUsername() : "system";
            String fullName = log.getUser() != null ? log.getUser().getFullName()  : "System";
            result.add(RecentActivityResponse.builder()
                    .username(username)
                    .fullName(fullName)
                    .action(log.getAction().name())
                    .entityType(log.getEntityType())
                    .ipAddress(log.getIpAddress())
                    .createdAt(log.getCreatedAt())
                    .build());
        }
        return result;
    }

    // ─── System Health ───────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     * <p>
     * CPU usage is obtained from {@link com.sun.management.OperatingSystemMXBean}
     * (system-wide CPU load). Memory metrics come from the JVM {@code Runtime}.
     * Disk metrics come from {@link java.io.File} for the filesystem root.
     * </p>
     */
    @Override
    @Transactional(readOnly = false) // no DB call; annotation not strictly needed
    public SystemHealthResponse getSystemHealth() {
        // CPU
        double cpuUsage = -1.0;
        try {
            OperatingSystemMXBean osBean =
                    (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            cpuUsage = osBean.getCpuLoad() * 100.0;
            if (cpuUsage < 0) cpuUsage = 0.0; // some JVMs return -1 initially
        } catch (Exception ignored) { /* not available on this JVM */ }

        // Memory
        Runtime runtime   = Runtime.getRuntime();
        long maxMemBytes   = runtime.maxMemory();
        long totalMemBytes = runtime.totalMemory();
        long freeMemBytes  = runtime.freeMemory();
        long usedMemBytes  = totalMemBytes - freeMemBytes;
        double memUsage    = maxMemBytes > 0
                ? (double) usedMemBytes / maxMemBytes * 100.0
                : 0.0;

        // Disk (root filesystem)
        File root        = new File("/");
        long totalDiskBytes = root.getTotalSpace();
        long usableDisk     = root.getUsableSpace();
        long usedDiskBytes  = totalDiskBytes - usableDisk;
        double diskUsage    = totalDiskBytes > 0
                ? (double) usedDiskBytes / totalDiskBytes * 100.0
                : 0.0;

        final long MB = 1024L * 1024;
        final long GB = 1024L * 1024 * 1024;

        return SystemHealthResponse.builder()
                .cpuUsagePercent(round2(cpuUsage))
                .memoryUsagePercent(round2(memUsage))
                .diskUsagePercent(round2(diskUsage))
                .totalMemoryMb(maxMemBytes / MB)
                .usedMemoryMb(usedMemBytes / MB)
                .totalDiskGb(totalDiskBytes / GB)
                .usedDiskGb(usedDiskBytes / GB)
                .build();
    }

    // ─── System Activity ─────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     * <p>
     * For {@code "24h"}: generates 24 hourly buckets from now − 23 h to now.
     * For {@code "7d"}: generates 7 daily buckets from now − 6 days to now.
     * Each bucket's count is derived from {@code countByCreatedAtAfter} with
     * successive time boundaries.
     * </p>
     */
    @Override
    public SystemActivityResponse getSystemActivity(String period) {
        OffsetDateTime now = OffsetDateTime.now();
        List<ActivityPoint> points = new ArrayList<>();

        if ("7d".equalsIgnoreCase(period)) {
            // 7 daily buckets
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
            for (int i = 6; i >= 0; i--) {
                OffsetDateTime dayStart = now.minusDays(i).toLocalDate()
                        .atStartOfDay(now.getOffset()).toOffsetDateTime();
                OffsetDateTime dayEnd   = dayStart.plusDays(1);
                long count = auditLogRepository.countByCreatedAtAfter(dayStart)
                           - auditLogRepository.countByCreatedAtAfter(dayEnd);
                if (count < 0) count = 0; // edge case safety
                points.add(ActivityPoint.builder()
                        .label(dayStart.format(fmt))
                        .count(count)
                        .build());
            }
        } else {
            // default: 24 hourly buckets
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:00");
            for (int i = 23; i >= 0; i--) {
                OffsetDateTime hourStart = now.minusHours(i).withMinute(0).withSecond(0).withNano(0);
                OffsetDateTime hourEnd   = hourStart.plusHours(1);
                long count = auditLogRepository.countByCreatedAtAfter(hourStart)
                           - auditLogRepository.countByCreatedAtAfter(hourEnd);
                if (count < 0) count = 0;
                points.add(ActivityPoint.builder()
                        .label(hourStart.format(fmt))
                        .count(count)
                        .build());
            }
        }

        return SystemActivityResponse.builder()
                .period("7d".equalsIgnoreCase(period) ? "7d" : "24h")
                .dataPoints(points)
                .build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Rounds a double value to two decimal places.
     *
     * @param value the value to round
     * @return value rounded to 2 decimal places
     */
    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
