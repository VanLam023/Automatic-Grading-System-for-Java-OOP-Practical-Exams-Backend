package agsfjope.backend.infrastructure.audit;

import agsfjope.backend.application.auditlogservices.AuditLogService;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.AuditAction;
import agsfjope.backend.infrastructure.security.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Utility class for manually recording audit log entries.
 * <p>
 * Use this as a <strong>fallback</strong> when the AOP-based {@link AuditLogAspect}
 * cannot cover the use case, for example:
 * <ul>
 *   <li>Login/logout events (no annotated service method)</li>
 *   <li>Scheduled job actions (no HTTP request context)</li>
 *   <li>Custom oldValues/newValues that need explicit construction</li>
 * </ul>
 * </p>
 *
 * <p>Usage:</p>
 * <pre>
 * auditLogHelper.log(AuditAction.LOGIN, "SESSION", null, null, null);
 * auditLogHelper.log(AuditAction.UPDATE, "EXAM", examId, oldJson, newJson);
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogHelper {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /**
     * Records an audit log entry with the given parameters.
     * Automatically resolves the current user, IP, and userAgent from context.
     *
     * @param action     the action type
     * @param entityType the affected entity type
     * @param entityId   UUID of the affected entity (may be null)
     * @param oldValues  previous state as Object (will be serialized to JSON)
     * @param newValues  new state as Object (will be serialized to JSON)
     */
    public void log(AuditAction action, String entityType, UUID entityId,
                    Object oldValues, Object newValues) {
        logWithExplicitUser(extractCurrentUserId(), action, entityType, entityId, oldValues, newValues);
    }

    /**
     * Records an audit log entry with an explicit user ID (e.g. for Login).
     */
    public void logWithExplicitUser(UUID explicitUserId, AuditAction action, String entityType, UUID entityId,
                                    Object oldValues, Object newValues) {
        try {
            String ipAddress = extractIpAddress();
            String userAgent = extractUserAgent();
            String oldJson = serializeToJson(oldValues);
            String newJson = serializeToJson(newValues);

            auditLogService.logAction(explicitUserId, action, entityType, entityId,
                    oldJson, newJson, ipAddress, userAgent);
        } catch (Exception e) {
            // Never let audit logging break the calling operation
            log.error("Failed to record audit log: {} on {} [{}]: {}",
                    action, entityType, entityId, e.getMessage(), e);
        }
    }

    /**
     * Overload accepting pre-serialized JSON strings for old/new values.
     */
    public void log(AuditAction action, String entityType, UUID entityId,
                    String oldValuesJson, String newValuesJson) {
        try {
            UUID userId = extractCurrentUserId();
            String ipAddress = extractIpAddress();
            String userAgent = extractUserAgent();

            auditLogService.logAction(userId, action, entityType, entityId,
                    oldValuesJson, newValuesJson, ipAddress, userAgent);
        } catch (Exception e) {
            log.error("Failed to record audit log: {} on {} [{}]: {}",
                    action, entityType, entityId, e.getMessage(), e);
        }
    }

    // ─── Context Extraction Helpers ──────────────────────────────────────────

    private UUID extractCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            User user = userDetails.getUser();
            return user.getUserId();
        }
        return null;
    }

    private String extractIpAddress() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractUserAgent() {
        HttpServletRequest request = getCurrentRequest();
        return (request != null) ? request.getHeader("User-Agent") : null;
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return (attrs != null) ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String serializeToJson(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String) {
            return (String) obj; // Already a JSON string
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize audit value to JSON: {}", e.getMessage());
            return null;
        }
    }
}
