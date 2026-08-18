package afds.africadatasolution.modules.external.smeplug;

import java.util.List;

public record NetworkPlans(String network, int networkId, List<SmePlugPlan> plans) {
}
