package afds.africadatasolution.modules.external.billstack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BillstackPaymentResponse(boolean status, String message, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String authorization_url, String access_code, String reference) {
    }
}
