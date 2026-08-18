package afds.africadatasolution.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataOrderRepository extends JpaRepository<DataOrder, UUID> {

    Page<DataOrder> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<DataOrder> findByIdAndUserId(UUID id, UUID userId);

    Optional<DataOrder> findByReferenceAndUserId(String reference, UUID userId);

    List<DataOrder> findByStatusAndUpdatedAtBefore(OrderStatus status, Instant cutoff, Pageable pageable);
}
