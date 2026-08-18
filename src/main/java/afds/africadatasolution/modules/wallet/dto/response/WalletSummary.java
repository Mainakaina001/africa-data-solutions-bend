package afds.africadatasolution.modules.wallet.dto.response;

import afds.africadatasolution.domain.wallet.Wallet;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletSummary(UUID id, BigDecimal balance, String currency) {

    public static WalletSummary from(Wallet wallet) {
        return new WalletSummary(wallet.getId(), wallet.getBalance(), wallet.getCurrency());
    }
}
