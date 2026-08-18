package afds.africadatasolution.domain.webhook;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks every inbound webhook by (provider, externalId) so replays are no-ops.
 * Mirrors the Prisma {@code WebhookEvent} model.
 */
@Entity
@Table(name = "webhook_events", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "external_id"}))
@Getter
@Setter
public class WebhookEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String provider;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "payload_hash", nullable = false)
    private String payloadHash;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(nullable = false)
    private String status = "RECEIVED";
}
