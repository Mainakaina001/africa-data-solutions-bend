package afds.africadatasolution.modules.bills.dto.response;

import afds.africadatasolution.domain.order.BillCategory;
import afds.africadatasolution.domain.order.BillPayment;
import afds.africadatasolution.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BillPaymentView(
        UUID id, BillCategory category, String serviceId, String variationCode, String billersCode, String phone,
        BigDecimal amount, String reference, OrderStatus status, String purchasedToken, String failureReason,
        Instant deliveredAt, Instant createdAt
) {
    public static BillPaymentView from(BillPayment p) {
        return new BillPaymentView(p.getId(), p.getCategory(), p.getServiceId(), p.getVariationCode(), p.getBillersCode(),
                p.getPhone(), p.getAmount(), p.getReference(), p.getStatus(), p.getPurchasedToken(), p.getFailureReason(),
                p.getDeliveredAt(), p.getCreatedAt());
    }
}
