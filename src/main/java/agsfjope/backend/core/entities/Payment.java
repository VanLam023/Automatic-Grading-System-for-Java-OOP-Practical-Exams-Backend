package agsfjope.backend.core.entities;

import agsfjope.backend.core.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "Payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "PaymentID")
    private UUID paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AppealID", nullable = false)
    private Appeal appeal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StudentID", nullable = false)
    private User student;

    @Column(name = "Amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "Currency", nullable = false, length = 10)
    @Builder.Default
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "Status", nullable = false, columnDefinition = "payment_status")
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "PayosOrderID", unique = true, length = 255)
    private String payosOrderId;

    @Column(name = "PayosPaymentLinkID", length = 255)
    private String payosPaymentLinkId;

    @Column(name = "QrCodeUrl", columnDefinition = "TEXT")
    private String qrCodeUrl;

    @Column(name = "CheckoutUrl", columnDefinition = "TEXT")
    private String checkoutUrl;

    @Column(name = "ExpiresAt", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "PaidAt")
    private OffsetDateTime paidAt;

    @Column(name = "RefundedAt")
    private OffsetDateTime refundedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "PayosWebhookData", columnDefinition = "JSONB")
    private String payosWebhookData;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "UpdatedAt", nullable = false)
    private OffsetDateTime updatedAt;

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
