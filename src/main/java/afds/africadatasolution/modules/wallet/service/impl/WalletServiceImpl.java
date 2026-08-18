package afds.africadatasolution.modules.wallet.service.impl;

import afds.africadatasolution.common.exception.ConflictException;
import afds.africadatasolution.common.exception.InsufficientBalanceException;
import afds.africadatasolution.common.exception.NotFoundException;
import afds.africadatasolution.common.exception.ValidationException;
import afds.africadatasolution.common.response.OffsetPage;
import afds.africadatasolution.common.config.properties.WalletProperties;
import afds.africadatasolution.domain.wallet.TransactionStatus;
import afds.africadatasolution.domain.wallet.TransactionType;
import afds.africadatasolution.domain.wallet.Wallet;
import afds.africadatasolution.domain.wallet.WalletRepository;
import afds.africadatasolution.domain.wallet.WalletTransaction;
import afds.africadatasolution.domain.wallet.WalletTransactionRepository;
import afds.africadatasolution.modules.wallet.WalletCreditCommand;
import afds.africadatasolution.modules.wallet.WalletDebitCommand;
import afds.africadatasolution.modules.wallet.service.WalletService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class WalletServiceImpl implements WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletServiceImpl.class);
    private static final Duration DAILY_RESET_WINDOW = Duration.ofHours(24);

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletProperties walletProperties;

    @PersistenceContext
    private EntityManager entityManager;

    public WalletServiceImpl(WalletRepository walletRepository,
                              WalletTransactionRepository walletTransactionRepository,
                              WalletProperties walletProperties) {
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.walletProperties = walletProperties;
    }

    @Transactional(readOnly = true)
    @Override
    public Wallet getWalletByUserId(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found"));
    }

    @Transactional(readOnly = true)
    @Override
    public BalanceView getBalance(UUID userId) {
        Wallet wallet = getWalletByUserId(userId);
        return new BalanceView(wallet.getBalance(), wallet.getCurrency());
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Override
    public WalletTransaction creditWallet(WalletCreditCommand cmd) {
        if (cmd.amount() == null || cmd.amount().signum() <= 0) {
            throw new ConflictException("Amount must be greater than zero");
        }

        var existing = walletTransactionRepository.findByReference(cmd.reference());
        if (existing.isPresent()) {
            log.warn("Duplicate credit reference (pre-check) reference={}", cmd.reference());
            return existing.get();
        }

        try {
            Wallet wallet = lockWallet(cmd.walletId());

            var dup = walletTransactionRepository.findByReference(cmd.reference());
            if (dup.isPresent()) return dup.get();

            BigDecimal balanceBefore = wallet.getBalance();
            BigDecimal balanceAfter = balanceBefore.add(cmd.amount());

            wallet.setBalance(balanceAfter);
            wallet.setVersion(wallet.getVersion() + 1);

            WalletTransaction txn = buildTransaction(wallet.getId(), TransactionType.CREDIT, cmd.amount(),
                    balanceBefore, balanceAfter, cmd.reference(), cmd.description(), cmd.metadata());
            walletTransactionRepository.save(txn);

            log.info("Wallet credited walletId={} reference={} amount={}", cmd.walletId(), cmd.reference(), cmd.amount());
            return txn;
        } catch (DataIntegrityViolationException e) {
            return recoverFromDuplicate(cmd.reference(), e);
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Override
    public WalletTransaction debitWallet(WalletDebitCommand cmd) {
        if (cmd.amount() == null || cmd.amount().signum() <= 0) {
            throw new ConflictException("Amount must be greater than zero");
        }

        BigDecimal perTxCap = BigDecimal.valueOf(walletProperties.perTxMax());
        if (cmd.amount().compareTo(perTxCap) > 0) {
            throw new ValidationException("Amount exceeds per-transaction limit of ₦" + perTxCap);
        }

        var existing = walletTransactionRepository.findByReference(cmd.reference());
        if (existing.isPresent()) {
            log.warn("Duplicate debit reference (pre-check) reference={}", cmd.reference());
            return existing.get();
        }

        try {
            Wallet wallet = lockWallet(cmd.walletId());

            var dup = walletTransactionRepository.findByReference(cmd.reference());
            if (dup.isPresent()) return dup.get();

            if (!cmd.skipVelocity()) {
                applyVelocityCap(wallet, cmd.amount());
            }

            BigDecimal balanceBefore = wallet.getBalance();
            BigDecimal balanceAfter = balanceBefore.subtract(cmd.amount());
            if (balanceAfter.signum() < 0) {
                throw new InsufficientBalanceException(
                        "Insufficient balance. Available: " + balanceBefore + ", Required: " + cmd.amount());
            }

            wallet.setBalance(balanceAfter);
            wallet.setVersion(wallet.getVersion() + 1);

            WalletTransaction txn = buildTransaction(wallet.getId(), TransactionType.DEBIT, cmd.amount(),
                    balanceBefore, balanceAfter, cmd.reference(), cmd.description(), cmd.metadata());
            walletTransactionRepository.save(txn);

            log.info("Wallet debited walletId={} reference={} amount={}", cmd.walletId(), cmd.reference(), cmd.amount());
            return txn;
        } catch (DataIntegrityViolationException e) {
            return recoverFromDuplicate(cmd.reference(), e);
        }
    }

    private void applyVelocityCap(Wallet wallet, BigDecimal amount) {
        BigDecimal cap = BigDecimal.valueOf(walletProperties.dailyDebitCap());
        Instant now = Instant.now();
        boolean resetExpired = Duration.between(wallet.getDailyDebitResetAt(), now).compareTo(DAILY_RESET_WINDOW) >= 0;

        BigDecimal newDailyTotal = resetExpired ? amount : wallet.getDailyDebitTotal().add(amount);
        if (newDailyTotal.compareTo(cap) > 0) {
            throw new ValidationException("Daily debit limit reached (cap ₦" + cap + ")");
        }

        wallet.setDailyDebitTotal(newDailyTotal);
        if (resetExpired) wallet.setDailyDebitResetAt(now);
    }

    /**
     * Acquires the Postgres row lock (SELECT ... FOR UPDATE) as the first
     * statement in the transaction. MUST be called before any other read of
     * this wallet inside the transaction.
     */
    private Wallet lockWallet(UUID walletId) {
        LockModeType lockMode = walletProperties.strictLocking()
                ? LockModeType.PESSIMISTIC_WRITE
                : LockModeType.NONE;
        Wallet wallet = entityManager.find(Wallet.class, walletId, lockMode);
        if (wallet == null) throw new NotFoundException("Wallet not found");
        return wallet;
    }

    private WalletTransaction buildTransaction(UUID walletId, TransactionType type, BigDecimal amount,
                                                BigDecimal balanceBefore, BigDecimal balanceAfter,
                                                String reference, String description,
                                                java.util.Map<String, Object> metadata) {
        WalletTransaction txn = new WalletTransaction();
        txn.setWalletId(walletId);
        txn.setType(type);
        txn.setAmount(amount);
        txn.setBalanceBefore(balanceBefore);
        txn.setBalanceAfter(balanceAfter);
        txn.setReference(reference);
        txn.setDescription(description);
        txn.setStatus(TransactionStatus.COMPLETED);
        txn.setMetadata(new java.util.HashMap<>(metadata));
        return txn;
    }

    /** Race: unique violation on reference from a concurrent request -> return the winner's row. */
    private WalletTransaction recoverFromDuplicate(String reference, DataIntegrityViolationException e) {
        return walletTransactionRepository.findByReference(reference)
                .orElseThrow(() -> e);
    }

    @Transactional(readOnly = true)
    @Override
    public OffsetPage<WalletTransaction> getTransactions(UUID userId, int limit, int offset) {
        Wallet wallet = getWalletByUserId(userId);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int safeOffset = Math.max(offset, 0);
        var items = walletTransactionRepository.findPage(wallet.getId(), safeLimit, safeOffset);
        long total = walletTransactionRepository.countByWalletId(wallet.getId());
        return OffsetPage.of(items, total, safeLimit, safeOffset);
    }

    @Transactional(readOnly = true)
    @Override
    public WalletTransaction getTransactionByReferenceForUser(String reference, UUID userId) {
        Wallet wallet = getWalletByUserId(userId);
        return walletTransactionRepository.findByReferenceAndWalletId(reference, wallet.getId())
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
    }
}
