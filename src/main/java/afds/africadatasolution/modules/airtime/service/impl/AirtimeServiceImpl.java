package afds.africadatasolution.modules.airtime.service.impl;

import afds.africadatasolution.modules.airtime.dto.request.AirtimePurchaseRequest;
import afds.africadatasolution.common.exception.ExternalServiceException;
import afds.africadatasolution.common.exception.FailureClassification;
import afds.africadatasolution.common.exception.InsufficientBalanceException;
import afds.africadatasolution.common.exception.NotFoundException;
import afds.africadatasolution.common.exception.ValidationException;
import afds.africadatasolution.common.response.NumberedPage;
import afds.africadatasolution.common.config.properties.WalletProperties;
import afds.africadatasolution.domain.order.AirtimeOrder;
import afds.africadatasolution.domain.order.AirtimeOrderRepository;
import afds.africadatasolution.domain.order.OrderStatus;
import afds.africadatasolution.domain.wallet.Wallet;
import afds.africadatasolution.modules.airtime.service.AirtimeService;
import afds.africadatasolution.modules.data.service.DataService;
import afds.africadatasolution.modules.external.vtpass.VtPassClient;
import afds.africadatasolution.modules.external.vtpass.VtPassPurchaseResponse;
import afds.africadatasolution.modules.outbox.service.OutboxService;
import afds.africadatasolution.modules.wallet.WalletCreditCommand;
import afds.africadatasolution.modules.wallet.WalletDebitCommand;
import afds.africadatasolution.modules.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Same commit-per-step design as {@link DataService} — see its class javadoc
 * for why these are plain (non-self-invoked-@Transactional) helper methods.
 */
@Service
public class AirtimeServiceImpl implements AirtimeService {

    private static final Logger log = LoggerFactory.getLogger(AirtimeServiceImpl.class);

    private final AirtimeOrderRepository airtimeOrderRepository;
    private final WalletService walletService;
    private final VtPassClient vtPassClient;
    private final OutboxService outboxService;
    private final WalletProperties walletProperties;

    public AirtimeServiceImpl(AirtimeOrderRepository airtimeOrderRepository, WalletService walletService,
                               VtPassClient vtPassClient, OutboxService outboxService, WalletProperties walletProperties) {
        this.airtimeOrderRepository = airtimeOrderRepository;
        this.walletService = walletService;
        this.vtPassClient = vtPassClient;
        this.outboxService = outboxService;
        this.walletProperties = walletProperties;
    }

    @Override
    public AirtimePurchaseResult purchase(UUID userId, AirtimePurchaseRequest request) {
        BigDecimal maxAmount = BigDecimal.valueOf(Math.min(50_000, walletProperties.perTxMax()));
        if (request.amount().compareTo(maxAmount) > 0) {
            throw new ValidationException("Maximum airtime amount is ₦" + maxAmount);
        }

        Wallet wallet = walletService.getWalletByUserId(userId);
        if (wallet.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. You need ₦" + request.amount() + " but have ₦" + wallet.getBalance());
        }

        String reference = "AIRTIME_" + System.currentTimeMillis() + "_" + userId.toString().substring(0, 8);
        String vtpassRequestId = vtPassClient.generateRequestId(userId);
        String walletTxnRef = "WALLET_" + reference;

        AirtimeOrder order = createPendingOrder(userId, request, reference, vtpassRequestId);

        try {
            walletService.debitWallet(new WalletDebitCommand(wallet.getId(), request.amount(), walletTxnRef,
                    "Airtime purchase for " + request.phone(),
                    Map.of("network", request.network(), "phone", request.phone(), "type", "airtime",
                            "orderId", order.getId().toString())));
        } catch (RuntimeException e) {
            markFailed(order.getId(), e.getMessage(), null);
            throw e;
        }
        markProcessing(order.getId(), walletTxnRef);

        try {
            VtPassPurchaseResponse response = vtPassClient.purchaseAirtime(vtpassRequestId, request.network(), request.amount(), request.phone());
            OrderStatus status = vtPassClient.resolveOrderStatus(response.code(), response.transactionStatus());
            completeOrder(order.getId(), status, response);

            log.info("Airtime purchase processed userId={} reference={} status={}", userId, reference, status);
            return new AirtimePurchaseResult(reference, vtpassRequestId, status);
        } catch (ExternalServiceException error) {
            return handleDeliveryFailure(order, wallet, reference, error);
        }
    }

    private AirtimeOrder createPendingOrder(UUID userId, AirtimePurchaseRequest request, String reference, String vtpassRequestId) {
        AirtimeOrder order = new AirtimeOrder();
        order.setUserId(userId);
        order.setNetwork(request.network());
        order.setPhone(request.phone());
        order.setAmount(request.amount());
        order.setReference(reference);
        order.setVtpassRequestId(vtpassRequestId);
        order.setStatus(OrderStatus.PENDING);
        return airtimeOrderRepository.save(order);
    }

    private void markFailed(UUID orderId, String reason, String refundTxnRef) {
        AirtimeOrder order = airtimeOrderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.FAILED);
        order.setFailureReason(reason);
        if (refundTxnRef != null) order.setRefundTxnRef(refundTxnRef);
        airtimeOrderRepository.save(order);
    }

    private void markProcessing(UUID orderId, String walletTxnRef) {
        AirtimeOrder order = airtimeOrderRepository.findById(orderId).orElseThrow();
        order.setWalletTxnRef(walletTxnRef);
        order.setStatus(OrderStatus.PROCESSING);
        airtimeOrderRepository.save(order);
    }

    private void completeOrder(UUID orderId, OrderStatus status, VtPassPurchaseResponse response) {
        AirtimeOrder order = airtimeOrderRepository.findById(orderId).orElseThrow();
        order.setStatus(status);
        order.setVtpassResponse(Map.of("code", response.code(), "status", String.valueOf(response.transactionStatus())));
        order.setDeliveredAt(status == OrderStatus.COMPLETED ? java.time.Instant.now() : null);
        airtimeOrderRepository.save(order);
    }

    private AirtimePurchaseResult handleDeliveryFailure(AirtimeOrder order, Wallet wallet, String reference,
                                                          ExternalServiceException error) {
        if (error.getClassification() == FailureClassification.DEFINITIVE_FAILURE) {
            String refundTxnRef = "REFUND_" + reference;
            walletService.creditWallet(new WalletCreditCommand(wallet.getId(), order.getAmount(), refundTxnRef,
                    "Refund for failed airtime purchase (" + reference + ")",
                    Map.of("originalReference", reference, "reason", "purchase_failed")));
            markFailed(order.getId(), error.getMessage(), refundTxnRef);
            throw error;
        }

        outboxService.enqueue("reconcile.airtime_order", order.getId().toString(),
                Map.of("reference", reference, "vtpassRequestId", String.valueOf(order.getVtpassRequestId())));
        throw new ValidationException("Airtime is taking longer than expected. If delivery fails your wallet will be refunded.");
    }

    @Transactional(readOnly = true)
    @Override
    public NumberedPage<AirtimeOrder> getHistory(UUID userId, int page, int limit) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.min(100, Math.max(1, limit));
        var result = airtimeOrderRepository.findByUserIdOrderByCreatedAtDesc(userId,
                org.springframework.data.domain.PageRequest.of(safePage - 1, safeLimit));
        return NumberedPage.of(result.getContent(), safePage, safeLimit, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    @Override
    public AirtimeOrder getByReference(UUID userId, String reference) {
        return airtimeOrderRepository.findByReferenceAndUserId(reference, userId)
                .orElseThrow(() -> new NotFoundException("Airtime order not found"));
    }
}
