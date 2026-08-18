package afds.africadatasolution.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillPaymentRepository extends JpaRepository<BillPayment, UUID> {

    Page<BillPayment> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<BillPayment> findByUserIdAndCategoryOrderByCreatedAtDesc(UUID userId, BillCategory category, Pageable pageable);

    Optional<BillPayment> findByReferenceAndUserId(String reference, UUID userId);

    List<BillPayment> findByStatusAndUpdatedAtBefore(OrderStatus status, Instant cutoff, Pageable pageable);
}
