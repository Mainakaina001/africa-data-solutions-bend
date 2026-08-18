package afds.africadatasolution.modules.data.service;

import afds.africadatasolution.common.response.OffsetPage;
import afds.africadatasolution.domain.catalog.DataPlan;
import afds.africadatasolution.domain.order.DataOrder;
import afds.africadatasolution.modules.data.dto.request.DataPurchaseRequest;
import afds.africadatasolution.modules.data.dto.response.DataPurchaseResponse;

import java.util.List;
import java.util.UUID;

/**
 * Wallet-debit + SME Plug delivery + refund-on-definite-failure orchestration.
 * Mirrors backend/src/services/data.service.ts.
 *
 * Pricing is always read from our own {@code data_plans} table — SME Plug is
 * asked to deliver, never asked what to charge. Refunds use the exact amount
 * debited (never the upstream wholesale price). On ambiguous delivery errors
 * the order is left PROCESSING for the reconciliation worker rather than
 * auto-refunded.
 */
public interface DataService {

    List<DataPlan> getDataPlans(String network);

    DataPlan getPlanById(UUID id);

    DataPurchaseResponseWithMessage purchaseData(UUID userId, DataPurchaseRequest request);

    OffsetPage<DataOrder> getUserOrders(UUID userId, int limit, int offset);

    DataOrder getOrderById(UUID orderId, UUID userId);

    record DataPurchaseResponseWithMessage(DataPurchaseResponse result, String message) {
    }
}
