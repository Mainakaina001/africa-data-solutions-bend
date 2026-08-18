package afds.africadatasolution.domain.wallet;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Mirrors the Prisma {@code Wallet} model. {@code version} is a manual
 * defense-in-depth counter bumped inside the pessimistic-locked transaction
 * (see WalletService) — it is NOT a JPA {@code @Version} optimistic lock,
 * since the row is already serialized via {@code SELECT ... FOR UPDATE}.
 */
@Entity
@Table(name = "wallets")
@Getter
@Setter
public class Wallet {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "NGN";

    @Column(nullable = false)
    private int version = 0;

    @Column(name = "daily_debit_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal dailyDebitTotal = BigDecimal.ZERO;

    @Column(name = "daily_debit_reset_at", nullable = false)
    private Instant dailyDebitResetAt = Instant.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
