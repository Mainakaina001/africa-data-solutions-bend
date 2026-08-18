package afds.africadatasolution.modules.airtime.service;

import afds.africadatasolution.common.response.NumberedPage;
import afds.africadatasolution.domain.order.AirtimeOrder;
import afds.africadatasolution.domain.order.OrderStatus;
import afds.africadatasolution.modules.airtime.dto.request.AirtimePurchaseRequest;

import java.util.UUID;

/**
 * Airtime purchase orchestration (SME Plug/VTPass — currently VTPass, mirrors
 * backend/src/controllers/airtime.controller.ts).
 */
public interface AirtimeService {

    AirtimePurchaseResult purchase(UUID userId, AirtimePurchaseRequest request);

    NumberedPage<AirtimeOrder> getHistory(UUID userId, int page, int limit);

    AirtimeOrder getByReference(UUID userId, String reference);

    record AirtimePurchaseResult(String reference, String vtpassRequestId, OrderStatus status) {
    }
}
