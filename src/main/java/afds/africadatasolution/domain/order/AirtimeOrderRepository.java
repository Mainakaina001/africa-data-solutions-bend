package afds.africadatasolution.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AirtimeOrderRepository extends JpaRepository<AirtimeOrder, UUID> {

    Page<AirtimeOrder> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<AirtimeOrder> findByReferenceAndUserId(String reference, UUID userId);

    List<AirtimeOrder> findByStatusAndUpdatedAtBefore(OrderStatus status, Instant cutoff, Pageable pageable);
}
