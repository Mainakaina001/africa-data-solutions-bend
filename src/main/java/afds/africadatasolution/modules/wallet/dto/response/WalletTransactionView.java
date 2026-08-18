package afds.africadatasolution.modules.wallet.dto.response;

import afds.africadatasolution.domain.wallet.TransactionStatus;
import afds.africadatasolution.domain.wallet.TransactionType;
import afds.africadatasolution.domain.wallet.WalletTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WalletTransactionView(
        UUID id, TransactionType type, BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
        String reference, String description, TransactionStatus status, Map<String, Object> metadata, Instant createdAt
) {
    public static WalletTransactionView from(WalletTransaction txn) {
        return new WalletTransactionView(txn.getId(), txn.getType(), txn.getAmount(), txn.getBalanceBefore(),
                txn.getBalanceAfter(), txn.getReference(), txn.getDescription(), txn.getStatus(), txn.getMetadata(),
                txn.getCreatedAt());
    }
}
