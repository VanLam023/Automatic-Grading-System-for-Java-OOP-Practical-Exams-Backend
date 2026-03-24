package agsfjope.backend.application.dtos.responses.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the "System Health" panel on the Admin Dashboard.
 * <p>
 * Contains real-time server-side resource usage metrics (CPU, RAM, disk)
 * useful for monitoring system performance without leaving the admin dashboard.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemHealthResponse {

    /**
     * Current CPU usage percentage of the system running the server (0–100).
     * Obtained from {@code OperatingSystemMXBean}.
     * Returns -1.0 if the metric is not available on the current JVM.
     */
    private double cpuUsagePercent;

    /**
     * Current JVM heap memory usage as a percentage of the maximum heap (0–100).
     * Calculated as {@code (totalMemory - freeMemory) / maxMemory * 100}.
     */
    private double memoryUsagePercent;

    /**
     * Disk space usage of the root filesystem as a percentage (0–100).
     * Calculated as {@code (totalSpace - usableSpace) / totalSpace * 100}.
     */
    private double diskUsagePercent;

    /** Total JVM heap size in megabytes (at time of request). */
    private long totalMemoryMb;

    /** Used JVM heap size in megabytes (at time of request). */
    private long usedMemoryMb;

    /** Total disk space in gigabytes for the root filesystem. */
    private long totalDiskGb;

    /** Used disk space in gigabytes for the root filesystem. */
    private long usedDiskGb;
}
