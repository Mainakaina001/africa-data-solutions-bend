package afds.africadatasolution.modules.wallet;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record WalletDebitCommand(
        UUID walletId,
        BigDecimal amount,
        String reference,
        String description,
        Map<String, Object> metadata,
        boolean skipVelocity
) {
    public WalletDebitCommand {
        metadata = metadata == null ? Map.of() : metadata;
    }

    public WalletDebitCommand(UUID walletId, BigDecimal amount, String reference, String description, Map<String, Object> metadata) {
        this(walletId, amount, reference, description, metadata, false);
    }
}
