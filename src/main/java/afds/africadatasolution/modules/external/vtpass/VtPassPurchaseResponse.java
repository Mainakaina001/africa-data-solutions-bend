package afds.africadatasolution.modules.external.vtpass;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Only the fields actually consumed are modeled — mirrors the relevant subset of
 * backend/src/types/index.ts#VTPassPurchaseResponse used by extractPurchasedToken / resolveOrderStatus. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VtPassPurchaseResponse(
        String code,
        String response_description,
        String requestId,
        String token,
        String purchased_code,
        String Pin,
        List<String> tokens,
        Content content
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(Transactions transactions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transactions(String status, String transactionId) {
    }

    public String transactionStatus() {
        return content != null && content.transactions() != null ? content.transactions().status() : null;
    }

    public String transactionId() {
        return content != null && content.transactions() != null ? content.transactions().transactionId() : null;
    }
}
