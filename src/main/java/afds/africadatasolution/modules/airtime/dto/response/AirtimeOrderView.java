package afds.africadatasolution.modules.airtime.dto.response;

import afds.africadatasolution.domain.order.AirtimeOrder;
import afds.africadatasolution.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AirtimeOrderView(
        UUID id, String network, String phone, BigDecimal amount, String reference, String vtpassRequestId,
        OrderStatus status, Map<String, Object> vtpassResponse, String failureReason, Instant deliveredAt, Instant createdAt
) {
    public static AirtimeOrderView summary(AirtimeOrder o) {
        return new AirtimeOrderView(o.getId(), o.getNetwork(), o.getPhone(), o.getAmount(), o.getReference(), null,
                o.getStatus(), null, null, o.getDeliveredAt(), o.getCreatedAt());
    }

    public static AirtimeOrderView detail(AirtimeOrder o) {
        return new AirtimeOrderView(o.getId(), o.getNetwork(), o.getPhone(), o.getAmount(), o.getReference(),
                o.getVtpassRequestId(), o.getStatus(), o.getVtpassResponse(), o.getFailureReason(), o.getDeliveredAt(),
                o.getCreatedAt());
    }
}
