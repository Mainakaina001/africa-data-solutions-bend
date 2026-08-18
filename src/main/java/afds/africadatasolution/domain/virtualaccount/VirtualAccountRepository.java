package afds.africadatasolution.domain.virtualaccount;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, UUID> {

    Optional<VirtualAccount> findFirstByUserIdAndIsActiveTrue(UUID userId);

    List<VirtualAccount> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<VirtualAccount> findByAccountNumber(String accountNumber);
}
