package agsfjope.backend.core.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "SystemConfigs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SystemConfigID")
    private Integer systemConfigId;

    @Column(name = "ConfigKey", nullable = false, unique = true, length = 100)
    private String configKey;

    @Column(name = "ConfigValue", nullable = false, columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "IsEncrypted", nullable = false)
    @Builder.Default
    private Boolean isEncrypted = false;

    @Column(name = "Description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UpdatedBy")
    private User updatedBy;

    @Column(name = "UpdatedAt", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
