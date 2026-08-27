package afds.africadatasolution.domain.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            OutboxStatus status, Instant now, Pageable pageable);

    // OutboxDispatcher.processOnce() calls this from a plain @Scheduled method with
    // no surrounding transaction — @Modifying queries need their own transaction to
    // execute at all, so it's declared here rather than relying on a caller-side one.
    @Transactional
    @Modifying
    @Query("update OutboxEvent o set o.status = 'PROCESSING' where o.id = :id and o.status = 'PENDING'")
    int claim(@Param("id") UUID id);
}
