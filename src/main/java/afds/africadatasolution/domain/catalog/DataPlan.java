package afds.africadatasolution.domain.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Mirrors the Prisma {@code DataPlan} model — our own retail catalog, never trust upstream pricing. */
@Entity
@Table(name = "data_plans")
@Getter
@Setter
public class DataPlan {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String network;

    @Column(name = "network_id", nullable = false)
    private int networkId;

    @Column(name = "sme_plug_plan_id", nullable = false)
    private int smePlugPlanId;

    @Column(name = "plan_code", nullable = false, unique = true)
    private String planCode;

    @Column(name = "plan_name", nullable = false)
    private String planName;

    @Column(name = "data_amount", nullable = false)
    private String dataAmount;

    /** Retail price billed to the user — source of truth, never the upstream live price. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Wholesale price for margin tracking only — never refund this. */
    @Column(name = "telco_price", precision = 10, scale = 2)
    private BigDecimal telcoPrice;

    @Column(nullable = false)
    private String validity;

    @Column(name = "plan_type", nullable = false)
    private String planType = "SME";

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
