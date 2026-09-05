package afds.africadatasolution.modules.external.paymentpoint;

import java.util.List;

public record PaymentPointVirtualAccountRequest(
        String email,
        String name,
        String phoneNumber,
        List<String> bankCode,
        String businessId
) {
}
