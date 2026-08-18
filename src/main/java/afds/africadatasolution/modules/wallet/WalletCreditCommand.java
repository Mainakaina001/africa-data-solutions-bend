package afds.africadatasolution.modules.wallet;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record WalletCreditCommand(
        UUID walletId,
        BigDecimal amount,
        String reference,
        String description,
        Map<String, Object> metadata
) {
    public WalletCreditCommand {
        metadata = metadata == null ? Map.of() : metadata;
    }
}
