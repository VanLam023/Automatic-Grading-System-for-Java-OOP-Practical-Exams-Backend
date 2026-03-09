package agsfjope.backend.core.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "Users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "UserID")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RoleID", nullable = false)
    private Role role;

    @Column(name = "Username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "Email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "PasswordHash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "FullName", nullable = false, length = 255)
    private String fullName;

    @Column(name = "MSSV", unique = true, length = 20)
    private String mssv;

    @Column(name = "Phone", length = 20)
    private String phone;

    @Column(name = "AvatarUrl", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "IsActive", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    @Column(name = "IsLocked", nullable = false)
    @Builder.Default
    private Boolean isLocked = false;

    @Column(name = "LoginFailCount", nullable = false)
    @Builder.Default
    private Integer loginFailCount = 0;

    @Column(name = "LockedUntil")
    private OffsetDateTime lockedUntil;

    @Column(name = "LastLoginAt")
    private OffsetDateTime lastLoginAt;

    @Column(name = "EmailVerifiedAt")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "UpdatedAt", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "DeletedAt")
    private OffsetDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
