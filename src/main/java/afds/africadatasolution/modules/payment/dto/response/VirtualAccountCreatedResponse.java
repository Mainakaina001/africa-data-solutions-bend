package afds.africadatasolution.modules.payment.dto.response;

public record VirtualAccountCreatedResponse(String accountNumber, String accountName, String bankName, String reference) {

    public static VirtualAccountCreatedResponse from(VirtualAccountSummary account) {
        return new VirtualAccountCreatedResponse(account.accountNumber(), account.accountName(), account.bankName(), account.accountReference());
    }
}
