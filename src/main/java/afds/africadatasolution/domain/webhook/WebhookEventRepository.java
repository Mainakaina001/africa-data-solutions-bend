package afds.africadatasolution.domain.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    Optional<WebhookEvent> findByProviderAndExternalId(String provider, String externalId);

    @Modifying
    @Query("update WebhookEvent w set w.status = :status, w.processedAt = :processedAt where w.provider = :provider and w.externalId = :externalId")
    void updateStatus(@Param("provider") String provider, @Param("externalId") String externalId,
                       @Param("status") String status, @Param("processedAt") Instant processedAt);
}
