package afds.africadatasolution.modules.data.dto.response;

import afds.africadatasolution.domain.catalog.DataPlan;

import java.math.BigDecimal;
import java.util.UUID;

public record DataPlanView(
        UUID id, String network, int networkId, String planCode, String planName,
        String dataAmount, BigDecimal price, String validity, String planType, String description
) {
    public static DataPlanView from(DataPlan plan) {
        return new DataPlanView(plan.getId(), plan.getNetwork(), plan.getNetworkId(), plan.getPlanCode(),
                plan.getPlanName(), plan.getDataAmount(), plan.getPrice(), plan.getValidity(), plan.getPlanType(),
                plan.getDescription());
    }
}
