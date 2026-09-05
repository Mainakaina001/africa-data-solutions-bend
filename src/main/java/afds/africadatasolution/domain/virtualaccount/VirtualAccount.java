package afds.africadatasolution.domain.virtualaccount;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Mirrors the Prisma {@code VirtualAccount} model. */
@Entity
@Table(name = "virtual_accounts")
@Getter
@Setter
public class VirtualAccount {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_reference", nullable = false, unique = true)
    private String accountReference;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "bank_code")
    private String bankCode;

    @Column(nullable = false)
    private String provider = "billstack";

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
