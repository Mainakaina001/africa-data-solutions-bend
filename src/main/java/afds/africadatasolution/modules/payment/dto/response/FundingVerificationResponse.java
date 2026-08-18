package afds.africadatasolution.modules.payment.dto.response;

import afds.africadatasolution.modules.wallet.dto.response.WalletTransactionView;

public record FundingVerificationResponse(String status, WalletTransactionView transaction) {
}
