package afds.africadatasolution.modules.external.vtpass;

import java.math.BigDecimal;

public record VtPassPurchaseCommand(
        String requestId,
        String serviceId,
        String billersCode,
        String variationCode,
        BigDecimal amount,
        String phone,
        String subscriptionType,
        Integer quantity
) {
}
