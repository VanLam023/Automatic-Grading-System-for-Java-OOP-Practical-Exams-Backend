package agsfjope.backend.infrastructure.audit;

import agsfjope.backend.application.auditlogservices.AuditLogService;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.enums.AuditAction;
import agsfjope.backend.infrastructure.security.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * AOP Aspect that automatically records audit log entries for methods
 * annotated with {@link Auditable}.
 * <p>
 * Flow:
 * <ol>
 *   <li>Intercept method call via {@code @Around} advice</li>
 *   <li>Extract current user, IP, and userAgent from SecurityContext + HttpServletRequest</li>
 *   <li>Execute the target method</li>
 *   <li>Serialize the return value as {@code newValues} JSON</li>
 *   <li>Persist the audit log via {@link AuditLogService#logAction}</li>
 * </ol>
 * </p>
 *
 * <p>Failures in audit logging are caught and logged — they never bubble up
 * to break the business operation.</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /**
     * Intercepts any method annotated with {@link Auditable} and records an audit log.
     *
     * @param joinPoint the join point representing the intercepted method
     * @param auditable the annotation instance carrying action and entityType
     * @return the original method's return value (unchanged)
     * @throws Throwable re-throws any exception from the target method
     */
    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        // Execute the actual method first — audit must not block business logic
        Object result = joinPoint.proceed();

        // Record audit log asynchronously in a try-catch — never break the caller
        try {
            recordAuditLog(auditable, result);
        } catch (Exception e) {
            log.error("Failed to record audit log for {}.{}: {}",
                    joinPoint.getTarget().getClass().getSimpleName(),
                    joinPoint.getSignature().getName(),
                    e.getMessage(), e);
        }

        return result;
    }

    /**
     * Builds and persists the audit log entry from the annotation metadata and context.
     */
    private void recordAuditLog(Auditable auditable, Object result) {
        AuditAction action = auditable.action();
        String entityType = auditable.entityType();

        // Extract current user from security context
        UUID userId = extractCurrentUserId();

        // Extract client metadata from HTTP request
        String ipAddress = extractIpAddress();
        String userAgent = extractUserAgent();

        // Serialize the return value as newValues (best-effort)
        String newValues = serializeToJson(result);

        // Persist
        auditLogService.logAction(userId, action, entityType, null, null, newValues, ipAddress, userAgent);
    }

    /**
     * Extracts the current authenticated user's UUID from SecurityContext.
     * Returns null if no authentication is present (e.g. system/scheduler context).
     */
    private UUID extractCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            User user = userDetails.getUser();
            return user.getUserId();
        }
        return null;
    }

    /**
     * Extracts the client IP address from the current HTTP request.
     * Handles X-Forwarded-For header for proxied requests.
     */
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

    /**
     * Extracts the User-Agent header from the current HTTP request.
     */
    private String extractUserAgent() {
        HttpServletRequest request = getCurrentRequest();
        return (request != null) ? request.getHeader("User-Agent") : null;
    }

    /**
     * Retrieves the current {@link HttpServletRequest} from Spring's RequestContextHolder.
     * Returns null when called outside of a web request context (e.g. scheduler).
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return (attrs != null) ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Serializes an object to a JSON string. Returns null on failure.
     */
    private String serializeToJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize audit log value: {}", e.getMessage());
            return null;
        }
    }
}
