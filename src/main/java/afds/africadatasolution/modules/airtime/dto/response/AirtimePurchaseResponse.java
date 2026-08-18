package afds.africadatasolution.modules.airtime.dto.response;

import afds.africadatasolution.domain.order.OrderStatus;

import java.math.BigDecimal;

public record AirtimePurchaseResponse(
        String reference, String vtpassRequestId, String phone, BigDecimal amount, String network, OrderStatus status
) {
}
