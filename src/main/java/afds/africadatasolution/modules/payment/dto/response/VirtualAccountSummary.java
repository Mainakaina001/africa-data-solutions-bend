package afds.africadatasolution.modules.payment.dto.response;

import afds.africadatasolution.domain.virtualaccount.VirtualAccount;

public record VirtualAccountSummary(String accountNumber, String accountName, String bankName, String accountReference) {

    public static VirtualAccountSummary from(VirtualAccount account) {
        return new VirtualAccountSummary(
                account.getAccountNumber(), account.getAccountName(), account.getBankName(), account.getAccountReference());
    }
}
