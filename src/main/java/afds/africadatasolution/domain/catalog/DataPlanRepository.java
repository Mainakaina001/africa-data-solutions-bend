package afds.africadatasolution.domain.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataPlanRepository extends JpaRepository<DataPlan, UUID> {

    List<DataPlan> findByIsActiveTrueOrderByNetworkAscPriceAsc();

    List<DataPlan> findByIsActiveTrueAndNetworkIgnoreCaseOrderByNetworkAscPriceAsc(String network);

    Optional<DataPlan> findByIdAndIsActiveTrue(UUID id);
}
