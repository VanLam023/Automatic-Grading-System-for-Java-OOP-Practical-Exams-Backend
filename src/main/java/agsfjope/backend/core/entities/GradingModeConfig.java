package agsfjope.backend.core.entities;

import agsfjope.backend.core.enums.GradingMode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "GradingModeConfigs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradingModeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "GradingModeConfigID")
    private Integer gradingModeConfigId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "Mode", nullable = false, unique = true, columnDefinition = "grading_mode")
    private GradingMode mode;

    @Column(name = "DisplayName", nullable = false, length = 100)
    private String displayName;

    @Column(name = "TestCaseWeight", nullable = false, precision = 5, scale = 2)
    private BigDecimal testCaseWeight;

    @Column(name = "OopWeight", nullable = false, precision = 5, scale = 2)
    private BigDecimal oopWeight;

    @Column(name = "StructureWeight", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal structureWeight = BigDecimal.ZERO;



    @Column(name = "OopCommentOnly", nullable = false)
    @Builder.Default
    private Boolean oopCommentOnly = false;

    @Column(name = "FailIfZeroTestCase", nullable = false)
    @Builder.Default
    private Boolean failIfZeroTestCase = false;

    @Column(name = "FailIfOopViolated", nullable = false)
    @Builder.Default
    private Boolean failIfOopViolated = false;

    @Column(name = "IsActive", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "Description", columnDefinition = "TEXT")
    private String description;
}
