package afds.africadatasolution.modules.data.dto.response;

import java.util.UUID;

public record DataPurchaseResponse(UUID orderId, boolean success) {
}
