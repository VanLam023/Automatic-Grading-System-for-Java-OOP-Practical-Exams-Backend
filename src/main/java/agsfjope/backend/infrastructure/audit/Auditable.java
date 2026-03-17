package agsfjope.backend.infrastructure.audit;

import agsfjope.backend.core.enums.AuditAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method for automatic audit log recording via AOP.
 * <p>
 * When a method annotated with {@code @Auditable} is invoked, the
 * {@link AuditLogAspect} intercepts it and records:
 * <ul>
 *   <li>The performing user (from SecurityContext)</li>
 *   <li>The action and entity type (from this annotation)</li>
 *   <li>The return value as {@code newValues} (serialized to JSON)</li>
 *   <li>The client IP and user agent (from HttpServletRequest)</li>
 * </ul>
 * </p>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code @Auditable(action = AuditAction.CREATE, entityType = "EXAM")}
 * public ExamResponse createExam(CreateExamRequest request) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * The audit action being performed.
     */
    AuditAction action();

    /**
     * The type of entity being acted upon (e.g. "EXAM", "SUBMISSION", "APPEAL").
     */
    String entityType();
}
