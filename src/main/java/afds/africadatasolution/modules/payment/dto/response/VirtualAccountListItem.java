package afds.africadatasolution.modules.payment.dto.response;

import afds.africadatasolution.domain.virtualaccount.VirtualAccount;

import java.time.Instant;
import java.util.UUID;

public record VirtualAccountListItem(UUID id, String accountNumber, String accountName, String bankName,
                                      String accountReference, boolean isActive, Instant createdAt) {
    public static VirtualAccountListItem from(VirtualAccount va) {
        return new VirtualAccountListItem(va.getId(), va.getAccountNumber(), va.getAccountName(), va.getBankName(),
                va.getAccountReference(), va.isActive(), va.getCreatedAt());
    }
}
