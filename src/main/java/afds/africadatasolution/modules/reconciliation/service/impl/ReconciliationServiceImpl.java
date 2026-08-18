package afds.africadatasolution.modules.reconciliation.service.impl;

import afds.africadatasolution.modules.audit.service.AuditService;
import afds.africadatasolution.domain.order.*;
import afds.africadatasolution.domain.wallet.TransactionType;
import afds.africadatasolution.domain.wallet.Wallet;
import afds.africadatasolution.domain.wallet.WalletRepository;
import afds.africadatasolution.domain.wallet.WalletTransactionRepository;
import afds.africadatasolution.modules.external.vtpass.VtPassClient;
import afds.africadatasolution.modules.external.vtpass.VtPassPurchaseResponse;
import afds.africadatasolution.modules.outbox.service.OutboxService;
import afds.africadatasolution.modules.reconciliation.service.ReconciliationService;
import afds.africadatasolution.modules.wallet.WalletCreditCommand;
import afds.africadatasolution.modules.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationServiceImpl.class);
    private static final java.time.Duration RECONCILE_AFTER = java.time.Duration.ofMinutes(2);
    private static final java.time.Duration MAX_AGE = java.time.Duration.ofHours(24);
    private static final int BATCH_SIZE = 50;

    private final DataOrderRepository dataOrderRepository;
    private final AirtimeOrderRepository airtimeOrderRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletService walletService;
    private final VtPassClient vtPassClient;
    private final OutboxService outboxService;
    private final AuditService auditService;

    public ReconciliationServiceImpl(DataOrderRepository dataOrderRepository, AirtimeOrderRepository airtimeOrderRepository,
                                      BillPaymentRepository billPaymentRepository, WalletRepository walletRepository,
                                      WalletTransactionRepository walletTransactionRepository, WalletService walletService,
                                      VtPassClient vtPassClient, OutboxService outboxService, AuditService auditService) {
        this.dataOrderRepository = dataOrderRepository;
        this.airtimeOrderRepository = airtimeOrderRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.walletService = walletService;
        this.vtPassClient = vtPassClient;
        this.outboxService = outboxService;
        this.auditService = auditService;
    }

    @Scheduled(fixedDelay = 60_000)
    @Override
    public void reconcileAll() {
        try {
            reconcileDataOrders();
            reconcileAirtimeOrders();
            reconcileBillPayments();
        } catch (Exception e) {
            log.error("Reconciliation loop error: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 30 * 60_000)
    @Override
    public void verifyWalletBalances() {
        for (Wallet wallet : walletRepository.findTop100ByOrderByUpdatedAtAsc()) {
            BigDecimal credits = walletTransactionRepository.sumByWalletIdAndType(wallet.getId(), TransactionType.CREDIT);
            BigDecimal debits = walletTransactionRepository.sumByWalletIdAndType(wallet.getId(), TransactionType.DEBIT);
            BigDecimal expected = credits.subtract(debits);

            if (expected.compareTo(wallet.getBalance()) != 0) {
                log.error("WALLET DRIFT DETECTED walletId={} userId={} storedBalance={} expected={}",
                        wallet.getId(), wallet.getUserId(), wallet.getBalance(), expected);
                auditService.log(wallet.getUserId(), "wallet_drift_detected", null, null,
                        Map.of("walletId", wallet.getId().toString(), "stored", wallet.getBalance().toString(),
                                "expected", expected.toString()),
                        false);
            }
        }
    }

    private void reconcileDataOrders() {
        Instant cutoff = Instant.now().minus(RECONCILE_AFTER);
        List<DataOrder> orders = dataOrderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.PROCESSING, cutoff, PageRequest.of(0, BATCH_SIZE));
        for (DataOrder order : orders) {
            try {
                java.time.Duration age = java.time.Duration.between(order.getCreatedAt(), Instant.now());
                if (age.compareTo(MAX_AGE) > 0) {
                    refundDataOrder(order);
                } else {
                    order.setLastReconciledAt(Instant.now());
                    dataOrderRepository.save(order);
                }
            } catch (Exception e) {
                log.error("Data order reconciliation failed orderId={} error={}", order.getId(), e.getMessage());
            }
        }
    }

    private void refundDataOrder(DataOrder order) {
        Wallet wallet = walletService.getWalletByUserId(order.getUserId());
        String refundRef = "REFUND_" + order.getReference();
        walletService.creditWallet(new WalletCreditCommand(wallet.getId(), order.getAmount(), refundRef,
                "Reconciliation refund for " + order.getReference(),
                Map.of("orderId", order.getId().toString(), "reason", "timeout")));

        order.setStatus(OrderStatus.REFUNDED);
        order.setRefundTxnRef(refundRef);
        order.setFailureReason("reconciliation:timeout");
        dataOrderRepository.save(order);

        outboxService.enqueue("notification.data_purchase_failed", order.getId().toString(),
                Map.of("userId", order.getUserId().toString(), "reason", "timeout"));
    }

    private void reconcileAirtimeOrders() {
        Instant cutoff = Instant.now().minus(RECONCILE_AFTER);
        List<AirtimeOrder> orders = airtimeOrderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.PROCESSING, cutoff, PageRequest.of(0, BATCH_SIZE));
        for (AirtimeOrder order : orders) {
            try {
                if (order.getVtpassRequestId() == null) continue;
                VtPassPurchaseResponse r = vtPassClient.requery(order.getVtpassRequestId());
                OrderStatus newStatus = vtPassClient.resolveOrderStatus(r.code(), r.transactionStatus());

                if (newStatus == OrderStatus.COMPLETED) {
                    order.setStatus(OrderStatus.COMPLETED);
                    order.setDeliveredAt(Instant.now());
                    order.setLastReconciledAt(Instant.now());
                    airtimeOrderRepository.save(order);
                } else if (newStatus == OrderStatus.FAILED) {
                    Wallet wallet = walletService.getWalletByUserId(order.getUserId());
                    String refundRef = "REFUND_" + order.getReference();
                    walletService.creditWallet(new WalletCreditCommand(wallet.getId(), order.getAmount(), refundRef,
                            "Reconciliation refund for " + order.getReference(),
                            Map.of("orderId", order.getId().toString(), "reason", "requery_failed")));
                    order.setStatus(OrderStatus.FAILED);
                    order.setRefundTxnRef(refundRef);
                    order.setFailureReason("reconciliation:requery_failed");
                    order.setLastReconciledAt(Instant.now());
                    airtimeOrderRepository.save(order);
                } else {
                    java.time.Duration age = java.time.Duration.between(order.getCreatedAt(), Instant.now());
                    if (age.compareTo(MAX_AGE) > 0) {
                        Wallet wallet = walletService.getWalletByUserId(order.getUserId());
                        String refundRef = "REFUND_" + order.getReference();
                        walletService.creditWallet(new WalletCreditCommand(wallet.getId(), order.getAmount(), refundRef,
                                "Reconciliation refund (24h timeout) " + order.getReference(),
                                Map.of("orderId", order.getId().toString(), "reason", "timeout")));
                        order.setStatus(OrderStatus.REFUNDED);
                        order.setRefundTxnRef(refundRef);
                        order.setFailureReason("reconciliation:timeout");
                        order.setLastReconciledAt(Instant.now());
                        airtimeOrderRepository.save(order);
                    } else {
                        order.setLastReconciledAt(Instant.now());
                        airtimeOrderRepository.save(order);
                    }
                }
            } catch (Exception e) {
                log.error("Airtime reconciliation failed orderId={} error={}", order.getId(), e.getMessage());
            }
        }
    }

    private void reconcileBillPayments() {
        Instant cutoff = Instant.now().minus(RECONCILE_AFTER);
        List<BillPayment> payments = billPaymentRepository.findByStatusAndUpdatedAtBefore(OrderStatus.PROCESSING, cutoff, PageRequest.of(0, BATCH_SIZE));
        for (BillPayment payment : payments) {
            try {
                VtPassPurchaseResponse r = vtPassClient.requery(payment.getReference());
                OrderStatus newStatus = vtPassClient.resolveOrderStatus(r.code(), r.transactionStatus());

                if (newStatus == OrderStatus.COMPLETED) {
                    payment.setStatus(OrderStatus.COMPLETED);
                    payment.setDeliveredAt(Instant.now());
                    payment.setLastReconciledAt(Instant.now());
                    payment.setPurchasedToken(vtPassClient.extractPurchasedToken(r));
                    billPaymentRepository.save(payment);
                } else if (newStatus == OrderStatus.FAILED) {
                    Wallet wallet = walletService.getWalletByUserId(payment.getUserId());
                    String refundRef = "REFUND_BILL_" + payment.getReference();
                    walletService.creditWallet(new WalletCreditCommand(wallet.getId(), payment.getAmount(), refundRef,
                            "Reconciliation refund for bill " + payment.getReference(),
                            Map.of("billPaymentId", payment.getId().toString(), "reason", "requery_failed")));
                    payment.setStatus(OrderStatus.FAILED);
                    payment.setRefundTxnRef(refundRef);
                    payment.setFailureReason("reconciliation:requery_failed");
                    payment.setLastReconciledAt(Instant.now());
                    billPaymentRepository.save(payment);
                } else {
                    payment.setLastReconciledAt(Instant.now());
                    billPaymentRepository.save(payment);
                }
            } catch (Exception e) {
                log.error("Bill reconciliation failed billPaymentId={} error={}", payment.getId(), e.getMessage());
            }
        }
    }
}
