package agsfjope.backend.core.entities;

import agsfjope.backend.core.enums.AuditAction;
import jakarta.persistence.*;
import lombok.*;

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
    @Column(name = "Action", nullable = false)
    private AuditAction action;

    @Column(name = "EntityType", nullable = false, length = 100)
    private String entityType;

    @Column(name = "EntityID")
    private UUID entityId;

    @Column(name = "OldValues", columnDefinition = "JSONB")
    private String oldValues;

    @Column(name = "NewValues", columnDefinition = "JSONB")
    private String newValues;

    @Column(name = "IpAddress", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "UserAgent", columnDefinition = "TEXT")
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
