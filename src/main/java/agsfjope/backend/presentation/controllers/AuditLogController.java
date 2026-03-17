package agsfjope.backend.presentation.controllers;

import agsfjope.backend.application.auditlogservices.AuditLogService;
import agsfjope.backend.application.dtos.responses.auditlog.AuditLogResponse;
import agsfjope.backend.core.enums.AuditAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Audit Log management (BR-48, TRG-008).
 * <p>
 * All endpoints are under {@code /api/admin/} and restricted to
 * {@code SYSTEM_ADMIN} / {@code ADMIN} roles via SecurityConfig.
 * Audit logs are immutable — no CREATE/UPDATE/DELETE endpoints are exposed.
 * </p>
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AuditLogController {

    private final AuditLogService auditLogService;

    // ─── GET /api/admin/audit-logs ───────────────────────────────────────────

    /**
     * Returns a paginated, filterable list of audit log entries.
     * System Admin uses this page to monitor system activity and investigate incidents.
     *
     * @param action     filter by action type (optional: CREATE, UPDATE, DELETE, LOGIN, etc.)
     * @param entityType filter by entity type (optional: EXAM, SUBMISSION, APPEAL, CONFIG, etc.)
     * @param userId     filter by performing user UUID (optional)
     * @param from       start of date range — ISO 8601 (optional)
     * @param to         end of date range — ISO 8601 (optional)
     * @param page       page number (0-indexed, default: 0)
     * @param size       page size (default: 20)
     * @return 200 with paginated audit log list
     */
    @GetMapping
    @Operation(summary = "Danh sách audit log (phân trang + filter)")
    public ResponseEntity<Map<String, Object>> getAuditLogs(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLogResponse> auditLogs = auditLogService.getAuditLogs(
                action, entityType, userId, from, to, pageable);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Lấy danh sách audit log thành công",
                "data", Map.of(
                        "content", auditLogs.getContent(),
                        "totalElements", auditLogs.getTotalElements(),
                        "totalPages", auditLogs.getTotalPages(),
                        "currentPage", auditLogs.getNumber(),
                        "pageSize", auditLogs.getSize()
                ),
                "errors", ""
        ));
    }

    // ─── GET /api/admin/audit-logs/{id} ──────────────────────────────────────

    /**
     * Returns the full detail of a single audit log entry.
     * Shows complete old/new values JSON for diff comparison.
     *
     * @param id the audit log UUID
     * @return 200 with the audit log detail
     */
    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết 1 audit log")
    public ResponseEntity<Map<String, Object>> getAuditLogById(@PathVariable UUID id) {
        AuditLogResponse auditLog = auditLogService.getAuditLogById(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Lấy chi tiết audit log thành công",
                "data", auditLog,
                "errors", ""
        ));
    }
}
