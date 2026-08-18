package afds.africadatasolution.modules.bills.dto.response;

import afds.africadatasolution.domain.order.OrderStatus;
import afds.africadatasolution.modules.bills.service.BillsService;

import java.math.BigDecimal;

public record BillPaymentResultResponse(
        String reference, String serviceID, String variationCode, BigDecimal amount, OrderStatus status,
        String token, String transactionId
) {
    public static BillPaymentResultResponse from(BillsService.BillPaymentResult result) {
        return new BillPaymentResultResponse(result.reference(), result.serviceId(), result.variationCode(),
                result.amount(), result.status(), result.token(), result.transactionId());
    }
}
