package agsfjope.backend.core.entities;

import agsfjope.backend.core.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "Notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "NotificationID")
    private UUID notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @Column(name = "Title", nullable = false, length = 255)
    private String title;

    @Column(name = "Body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "Type", nullable = false, columnDefinition = "notification_type")
    @Builder.Default
    private NotificationType type = NotificationType.IN_APP;

    @Column(name = "RelatedEntityType", length = 50)
    private String relatedEntityType;

    @Column(name = "RelatedEntityID")
    private UUID relatedEntityId;

    @Column(name = "IsRead", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "ReadAt")
    private OffsetDateTime readAt;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
