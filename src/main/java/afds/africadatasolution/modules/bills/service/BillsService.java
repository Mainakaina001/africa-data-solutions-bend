package afds.africadatasolution.modules.bills.service;

import afds.africadatasolution.common.response.NumberedPage;
import afds.africadatasolution.domain.order.BillCategory;
import afds.africadatasolution.domain.order.BillPayment;
import afds.africadatasolution.domain.order.OrderStatus;
import afds.africadatasolution.modules.external.vtpass.VtPassVariationsResponse;
import afds.africadatasolution.modules.external.vtpass.VtPassVerifyResponse;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Electricity / TV / education bill payments via VTPass.
 * Mirrors backend/src/controllers/bills.controller.ts.
 *
 * Fixed-price variations (TV, education) always charge VTPass's canonical
 * variation amount — never the client-supplied one — closing the
 * "debit ₦100, deliver ₦20k package" abuse the client amount would otherwise
 * allow. Electricity is variable-price (prepaid units), so the client amount
 * is trusted within min/max bounds.
 */
public interface BillsService {

    VtPassVariationsResponse getVariations(String serviceId);

    VtPassVerifyResponse verifyMeter(String meterNumber, String serviceId, String type);

    VtPassVerifyResponse verifySmartcard(String smartcardNumber, String serviceId);

    VtPassVerifyResponse verifyJambProfile(String profileId, String variationCode);

    BillPaymentResult payElectricity(UUID userId, String meterNumber, String serviceId, String variationCode,
                                      BigDecimal amount, String phone);

    BillPaymentResult payTv(UUID userId, String smartcardNumber, String serviceId, String variationCode,
                             BigDecimal amount, String phone, String subscriptionType);

    BillPaymentResult payEducation(UUID userId, String serviceId, String variationCode, BigDecimal amount,
                                    String phone, int quantity);

    NumberedPage<BillPayment> getHistory(UUID userId, BillCategory category, int page, int limit);

    BillPayment getByReference(UUID userId, String reference);

    record BillPaymentResult(String reference, String serviceId, String variationCode, BigDecimal amount,
                              OrderStatus status, String token, String transactionId) {
    }
}
