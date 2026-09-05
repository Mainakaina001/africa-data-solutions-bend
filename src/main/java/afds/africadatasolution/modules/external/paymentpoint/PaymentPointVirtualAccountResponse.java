package afds.africadatasolution.modules.external.paymentpoint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentPointVirtualAccountResponse(
        String status,
        String message,
        Customer customer,
        List<BankAccount> bankAccounts
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(
            @JsonProperty("customer_id") String customerId,
            @JsonProperty("customer_name") String customerName,
            @JsonProperty("customer_email") String customerEmail,
            @JsonProperty("customer_phone_number") String customerPhoneNumber
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BankAccount(
            String bankCode,
            String accountNumber,
            String accountName,
            String bankName,
            @JsonProperty("Reserved_Account_Id") String reservedAccountId
    ) {
    }
}
