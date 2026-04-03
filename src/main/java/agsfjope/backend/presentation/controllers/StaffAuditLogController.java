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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Staff-facing read-only audit log endpoints.
 * Added separately to avoid touching the existing admin controller contract.
 */
@RestController
@RequestMapping("/api/staff/audit-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('EXAM_STAFF')")
public class StaffAuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @Operation(summary = "Danh sách audit log cho Exam Staff")
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

        Map<String, Object> data = new HashMap<>();
        data.put("content", auditLogs.getContent());
        data.put("totalElements", auditLogs.getTotalElements());
        data.put("totalPages", auditLogs.getTotalPages());
        data.put("currentPage", auditLogs.getNumber());
        data.put("pageSize", auditLogs.getSize());

        return ResponseEntity.ok(buildResponse("Lấy danh sách audit log thành công", data));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết 1 audit log cho Exam Staff")
    public ResponseEntity<Map<String, Object>> getAuditLogById(@PathVariable UUID id) {
        AuditLogResponse auditLog = auditLogService.getAuditLogById(id);
        return ResponseEntity.ok(buildResponse("Lấy chi tiết audit log thành công", auditLog));
    }

    private Map<String, Object> buildResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        response.put("errors", null);
        return response;
    }
}