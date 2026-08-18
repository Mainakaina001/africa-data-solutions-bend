package afds.africadatasolution.modules.external.billstack;

import java.util.Map;

public record BillstackPaymentRequest(
        long amount,
        String email,
        String reference,
        String callback_url,
        Map<String, Object> metadata
) {
}
