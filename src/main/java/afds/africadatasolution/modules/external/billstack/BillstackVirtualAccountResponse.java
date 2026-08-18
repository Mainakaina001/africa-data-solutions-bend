package afds.africadatasolution.modules.external.billstack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BillstackVirtualAccountResponse(boolean status, String message, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String reference, List<Account> account) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(
            @JsonProperty("account_number") String accountNumber,
            @JsonProperty("account_name") String accountName,
            @JsonProperty("bank_name") String bankName,
            @JsonProperty("bank_id") String bankId,
            @JsonProperty("created_at") String createdAt
    ) {
    }
}
