package afds.africadatasolution.modules.data.dto.response;

import afds.africadatasolution.domain.order.DataOrder;
import afds.africadatasolution.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DataOrderView(
        UUID id, String phone, BigDecimal amount, String reference, OrderStatus status,
        String failureReason, Instant deliveredAt, Instant createdAt
) {
    public static DataOrderView from(DataOrder order) {
        return new DataOrderView(order.getId(), order.getPhone(), order.getAmount(), order.getReference(),
                order.getStatus(), order.getFailureReason(), order.getDeliveredAt(), order.getCreatedAt());
    }
}
