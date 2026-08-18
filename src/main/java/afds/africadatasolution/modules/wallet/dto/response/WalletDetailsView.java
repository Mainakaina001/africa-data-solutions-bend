package afds.africadatasolution.modules.wallet.dto.response;

import afds.africadatasolution.domain.wallet.Wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletDetailsView(UUID id, BigDecimal balance, String currency, Instant createdAt, Instant updatedAt) {

    public static WalletDetailsView from(Wallet wallet) {
        return new WalletDetailsView(wallet.getId(), wallet.getBalance(), wallet.getCurrency(),
                wallet.getCreatedAt(), wallet.getUpdatedAt());
    }
}
