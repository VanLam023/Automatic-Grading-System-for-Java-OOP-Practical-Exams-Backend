package agsfjope.backend.core.entities;

import agsfjope.backend.core.enums.AuditAction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "AuditLogs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "AuditLogID")
    private UUID auditLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID")
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "Action", nullable = false, columnDefinition = "audit_action")
    private AuditAction action;

    @Column(name = "EntityType", nullable = false, length = 100)
    private String entityType;

    @Column(name = "EntityID")
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "OldValues")
    private String oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "NewValues")
    private String newValues;

    @Column(name = "IpAddress", length = 45)
    private String ipAddress;

    @Column(name = "UserAgent")
    private String userAgent;

    @Column(name = "CorrelationID")
    private UUID correlationId;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
