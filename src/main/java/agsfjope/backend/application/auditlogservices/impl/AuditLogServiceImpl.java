package agsfjope.backend.application.auditlogservices.impl;

import agsfjope.backend.application.auditlogservices.AuditLogService;
import agsfjope.backend.application.dtos.responses.auditlog.AuditLogResponse;
import agsfjope.backend.core.entities.AuditLog;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.AuditAction;
import agsfjope.backend.core.repositories.auditlog.AuditLogRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link AuditLogService}.
 * <p>
 * Provides:
 * <ul>
 *   <li>Dynamic filtered + paginated listing for System Admin dashboard</li>
 *   <li>Detail view of a single audit log entry</li>
 *   <li>Internal log recording (called by AOP aspect or AuditLogHelper)</li>
 *   <li>SCH-004 scheduled cleanup of old records</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    // ─── Read Operations ─────────────────────────────────────────────────────

    /**
     * Returns a paginated list of audit logs, optionally filtered by
     * action, entityType, userId, and date range.
     * Uses JPA Specifications for dynamic query composition.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogs(AuditAction action, String entityType,
                                                UUID userId, OffsetDateTime from,
                                                OffsetDateTime to, Pageable pageable) {
        Specification<AuditLog> spec = buildSpecification(action, entityType, userId, from, to);
        return auditLogRepository.findAll(spec, pageable).map(this::mapToResponse);
    }

    /**
     * Returns the full detail of a single audit log entry.
     *
     * @throws EntityNotFoundException if the audit log ID does not exist
     */
    @Override
    @Transactional(readOnly = true)
    public AuditLogResponse getAuditLogById(UUID auditLogId) {
        AuditLog auditLog = auditLogRepository.findById(auditLogId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Audit log không tồn tại với id: " + auditLogId));
        return mapToResponse(auditLog);
    }

    // ─── Write Operations ────────────────────────────────────────────────────

    /**
     * TRG-008: Persists a new audit log entry.
     * Called by {@code AuditLogAspect} (automatic) or {@code AuditLogHelper} (manual).
     */
    @Override
    @Transactional
    public void logAction(UUID userId, AuditAction action, String entityType,
                          UUID entityId, String oldValues, String newValues,
                          String ipAddress, String userAgent) {
        // Build a User reference — only the FK ID is needed
        User userRef = (userId != null) ? User.builder().userId(userId).build() : null;

        AuditLog auditLog = AuditLog.builder()
                .user(userRef)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .oldValues(oldValues)
                .newValues(newValues)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        auditLogRepository.save(auditLog);
        log.debug("Audit log recorded: {} on {} [{}]", action, entityType, entityId);
    }

    // ─── SCH-004 Cleanup ─────────────────────────────────────────────────────

    /**
     * SCH-004: Deletes audit logs older than the retention period.
     *
     * @param retentionDays number of days to keep logs
     * @return number of deleted records
     */
    @Override
    @Transactional
    public int cleanupOldLogs(int retentionDays) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(retentionDays);
        int deleted = auditLogRepository.deleteByCreatedAtBefore(cutoff);
        log.info("SCH-004: Deleted {} audit logs older than {} days (cutoff: {})",
                deleted, retentionDays, cutoff);
        return deleted;
    }

    // ─── Specification Builder ───────────────────────────────────────────────

    /**
     * Builds a JPA Specification from the optional filter parameters.
     * Null parameters are ignored — only non-null values become WHERE clauses.
     */
    private Specification<AuditLog> buildSpecification(AuditAction action, String entityType,
                                                        UUID userId, OffsetDateTime from,
                                                        OffsetDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("userId"), userId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            // Default sort: newest first
            query.orderBy(cb.desc(root.get("createdAt")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    /**
     * Maps an {@link AuditLog} entity to an {@link AuditLogResponse} DTO.
     * Safely handles null user (system-generated events).
     */
    private AuditLogResponse mapToResponse(AuditLog auditLog) {
        String username = null;
        String role = null;

        if (auditLog.getUser() != null) {
            username = auditLog.getUser().getUsername();
            if (auditLog.getUser().getRole() != null) {
                role = auditLog.getUser().getRole().getName();
            }
        }

        return AuditLogResponse.builder()
                .auditLogId(auditLog.getAuditLogId())
                .username(username)
                .role(role)
                .action(auditLog.getAction().name())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .oldValues(auditLog.getOldValues())
                .newValues(auditLog.getNewValues())
                .ipAddress(auditLog.getIpAddress())
                .userAgent(auditLog.getUserAgent())
                .correlationId(auditLog.getCorrelationId())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}
