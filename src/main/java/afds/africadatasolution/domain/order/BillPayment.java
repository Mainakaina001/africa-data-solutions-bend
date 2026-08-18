package afds.africadatasolution.domain.order;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Mirrors the Prisma {@code BillPayment} model. */
@Entity
@Table(name = "bill_payments")
@Getter
@Setter
public class BillPayment {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private BillCategory category;

    @Column(name = "service_id", nullable = false)
    private String serviceId;

    @Column(name = "variation_code", nullable = false)
    private String variationCode;

    @Column(name = "billers_code", nullable = false)
    private String billersCode;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, unique = true)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private OrderStatus status = OrderStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vtpass_response", columnDefinition = "jsonb")
    private Map<String, Object> vtpassResponse = new HashMap<>();

    @Column(name = "purchased_token")
    private String purchasedToken;

    @Column(name = "wallet_txn_ref", unique = true)
    private String walletTxnRef;

    @Column(name = "refund_txn_ref", unique = true)
    private String refundTxnRef;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "last_reconciled_at")
    private Instant lastReconciledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
