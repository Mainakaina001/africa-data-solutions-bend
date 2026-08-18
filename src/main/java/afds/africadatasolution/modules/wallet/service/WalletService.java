package afds.africadatasolution.modules.wallet.service;

import afds.africadatasolution.common.response.OffsetPage;
import afds.africadatasolution.domain.wallet.Wallet;
import afds.africadatasolution.domain.wallet.WalletTransaction;
import afds.africadatasolution.modules.wallet.WalletCreditCommand;
import afds.africadatasolution.modules.wallet.WalletDebitCommand;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Wallet ledger operations.
 *
 * Concurrency model — mirrors backend/src/services/wallet.service.ts, but
 * implemented directly against the JPA EntityManager: every credit/debit
 * acquires a {@code SELECT ... FOR UPDATE} row lock as the FIRST statement
 * inside a SERIALIZABLE transaction, so concurrent debits on the same wallet
 * queue safely instead of racing. Idempotency is enforced by a unique
 * {@code reference} column, checked both before and after acquiring the lock
 * to close the race window; a unique-constraint violation on insert
 * (concurrent duplicate) falls back to returning the winning row.
 *
 * Money is BigDecimal end-to-end — never cast a wallet balance to double.
 */
public interface WalletService {

    Wallet getWalletByUserId(UUID userId);

    BalanceView getBalance(UUID userId);

    /** Credit a wallet. Safe under concurrency via row lock. Idempotent on {@code reference}. */
    WalletTransaction creditWallet(WalletCreditCommand cmd);

    /** Debit a wallet. Atomic balance check + decrement, velocity-capped. Idempotent on {@code reference}. */
    WalletTransaction debitWallet(WalletDebitCommand cmd);

    OffsetPage<WalletTransaction> getTransactions(UUID userId, int limit, int offset);

    WalletTransaction getTransactionByReferenceForUser(String reference, UUID userId);

    record BalanceView(BigDecimal balance, String currency) {
    }
}
