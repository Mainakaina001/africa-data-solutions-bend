package afds.africadatasolution.modules.data.service.impl;

import afds.africadatasolution.common.exception.ExternalServiceException;
import afds.africadatasolution.common.exception.FailureClassification;
import afds.africadatasolution.common.exception.InsufficientBalanceException;
import afds.africadatasolution.common.exception.NotFoundException;
import afds.africadatasolution.common.exception.ValidationException;
import afds.africadatasolution.common.response.OffsetPage;
import afds.africadatasolution.modules.data.dto.request.DataPurchaseRequest;
import afds.africadatasolution.modules.data.dto.response.DataPurchaseResponse;
import afds.africadatasolution.domain.catalog.DataPlan;
import afds.africadatasolution.domain.catalog.DataPlanRepository;
import afds.africadatasolution.domain.order.DataOrder;
import afds.africadatasolution.domain.order.DataOrderRepository;
import afds.africadatasolution.domain.order.OrderStatus;
import afds.africadatasolution.domain.wallet.Wallet;
import afds.africadatasolution.modules.data.service.DataService;
import afds.africadatasolution.modules.external.smeplug.SmePlugClient;
import afds.africadatasolution.modules.external.smeplug.SmePlugDataRequest;
import afds.africadatasolution.modules.external.smeplug.SmePlugDataResponse;
import afds.africadatasolution.modules.outbox.service.OutboxService;
import afds.africadatasolution.modules.wallet.WalletCreditCommand;
import afds.africadatasolution.modules.wallet.WalletDebitCommand;
import afds.africadatasolution.modules.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Each step below is its own atomic unit of work — committed immediately via an
 * explicit save() rather than one long-lived transaction, matching
 * backend/src/services/data.service.ts (which also issues independent Prisma
 * calls around the external HTTP call: a DB transaction cannot be safely held
 * open across a slow network round-trip to SME Plug).
 *
 * NOTE: the private helpers below are plain (non-@Transactional) methods by
 * design — Spring's proxy-based AOP does not intercept self-invocation, so a
 * @Transactional annotation on a method called via {@code this.} from
 * elsewhere in this class would silently be ignored. Persistence here relies
 * on the explicit repository.save() call (which merges the detached,
 * already-ID'd entity in its own transaction), not on dirty-checking a still-managed entity.
 */
@Service
public class DataServiceImpl implements DataService {

    private static final Logger log = LoggerFactory.getLogger(DataServiceImpl.class);

    private final DataPlanRepository dataPlanRepository;
    private final DataOrderRepository dataOrderRepository;
    private final WalletService walletService;
    private final SmePlugClient smePlugClient;
    private final OutboxService outboxService;

    public DataServiceImpl(DataPlanRepository dataPlanRepository, DataOrderRepository dataOrderRepository,
                            WalletService walletService, SmePlugClient smePlugClient, OutboxService outboxService) {
        this.dataPlanRepository = dataPlanRepository;
        this.dataOrderRepository = dataOrderRepository;
        this.walletService = walletService;
        this.smePlugClient = smePlugClient;
        this.outboxService = outboxService;
    }

    @Transactional(readOnly = true)
    @Override
    public List<DataPlan> getDataPlans(String network) {
        return network == null || network.isBlank()
                ? dataPlanRepository.findByIsActiveTrueOrderByNetworkAscPriceAsc()
                : dataPlanRepository.findByIsActiveTrueAndNetworkIgnoreCaseOrderByNetworkAscPriceAsc(network);
    }

    @Transactional(readOnly = true)
    @Override
    public DataPlan getPlanById(UUID id) {
        return dataPlanRepository.findByIdAndIsActiveTrue(id).orElseThrow(() -> new NotFoundException("Data plan not found"));
    }

    @Override
    public DataPurchaseResponseWithMessage purchaseData(UUID userId, DataPurchaseRequest request) {
        DataPlan plan = getPlanById(request.dataPlanId());

        String formattedPhone = smePlugClient.formatPhoneNumber(request.phone());
        if (!smePlugClient.validatePhoneNumber(formattedPhone, plan.getNetwork())) {
            throw new ValidationException("Invalid " + plan.getNetwork() + " phone number: " + request.phone());
        }

        Wallet wallet = walletService.getWalletByUserId(userId);
        if (wallet.getBalance().compareTo(plan.getPrice()) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Required: ₦" + plan.getPrice() + ", Available: ₦" + wallet.getBalance());
        }

        String orderReference = "ORDER_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        String walletTxnRef = "DEBIT_" + orderReference;

        DataOrder order = createPendingOrder(userId, plan, formattedPhone, orderReference);

        try {
            debitForOrder(order, wallet, plan.getPrice(), walletTxnRef, formattedPhone, plan);
        } catch (RuntimeException e) {
            markOrderFailed(order.getId(), e.getMessage());
            throw e;
        }
        markOrderProcessing(order.getId(), walletTxnRef);

        try {
            SmePlugDataResponse smeResponse = smePlugClient.purchaseData(
                    new SmePlugDataRequest(plan.getNetworkId(), plan.getSmePlugPlanId(), formattedPhone));

            completeOrder(order.getId(), smeResponse);
            outboxService.enqueue("notification.data_purchase_success", order.getId().toString(),
                    Map.of("userId", userId.toString(), "dataAmount", plan.getDataAmount(), "phone", formattedPhone));

            return new DataPurchaseResponseWithMessage(new DataPurchaseResponse(order.getId(), true),
                    plan.getDataAmount() + " data successfully sent to " + formattedPhone);
        } catch (ExternalServiceException smeError) {
            return handleDeliveryFailure(order.getId(), userId, wallet, plan, orderReference, walletTxnRef, smeError);
        }
    }

    private DataOrder createPendingOrder(UUID userId, DataPlan plan, String formattedPhone, String orderReference) {
        DataOrder order = new DataOrder();
        order.setUserId(userId);
        order.setDataPlanId(plan.getId());
        order.setPhone(formattedPhone);
        order.setAmount(plan.getPrice());
        order.setReference(orderReference);
        order.setStatus(OrderStatus.PENDING);
        return dataOrderRepository.save(order);
    }

    private void debitForOrder(DataOrder order, Wallet wallet, java.math.BigDecimal price, String walletTxnRef,
                                String formattedPhone, DataPlan plan) {
        walletService.debitWallet(new WalletDebitCommand(wallet.getId(), price, walletTxnRef,
                "Data purchase: " + plan.getPlanName() + " for " + formattedPhone,
                Map.of("orderId", order.getId().toString(), "orderReference", order.getReference(),
                        "dataPlanId", plan.getId().toString(), "phone", formattedPhone)));
    }

    private void markOrderFailed(UUID orderId, String reason) {
        DataOrder order = dataOrderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.FAILED);
        order.setFailureReason(reason);
        dataOrderRepository.save(order);
    }

    private void markOrderProcessing(UUID orderId, String walletTxnRef) {
        DataOrder order = dataOrderRepository.findById(orderId).orElseThrow();
        order.setWalletTxnRef(walletTxnRef);
        order.setStatus(OrderStatus.PROCESSING);
        dataOrderRepository.save(order);
    }

    private void completeOrder(UUID orderId, SmePlugDataResponse smeResponse) {
        DataOrder order = dataOrderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.COMPLETED);
        order.setSmePlugResponse(Map.of(
                "status", smeResponse.status(),
                "data", smeResponse.data() != null ? Map.of("reference", String.valueOf(smeResponse.data().reference()),
                        "msg", String.valueOf(smeResponse.data().msg())) : Map.of()));
        order.setDeliveredAt(Instant.now());
        dataOrderRepository.save(order);
    }

    private DataPurchaseResponseWithMessage handleDeliveryFailure(UUID orderId, UUID userId, Wallet wallet, DataPlan plan,
                                                                    String orderReference, String walletTxnRef,
                                                                    ExternalServiceException smeError) {
        FailureClassification classification = smeError.getClassification();
        log.warn("SMEPlug delivery error orderId={} error={} classification={}", orderId, smeError.getMessage(), classification);

        if (classification == FailureClassification.DEFINITIVE_FAILURE) {
            String refundRef = "REFUND_" + orderReference;
            walletService.creditWallet(new WalletCreditCommand(wallet.getId(), plan.getPrice(), refundRef,
                    "Refund for failed data purchase: " + orderReference,
                    Map.of("orderId", orderId.toString(), "originalTxnRef", walletTxnRef, "reason", smeError.getMessage())));

            refundOrder(orderId, refundRef, smeError.getMessage());
            outboxService.enqueue("notification.data_purchase_failed", orderId.toString(),
                    Map.of("userId", userId.toString(), "dataAmount", plan.getDataAmount(), "reason", smeError.getMessage()));

            throw new ValidationException("Data delivery failed: " + smeError.getMessage() + ". Your wallet has been refunded.");
        }

        throw new ValidationException("Data delivery is taking longer than expected. "
                + "You will be notified once it completes; if it fails your wallet will be refunded.");
    }

    private void refundOrder(UUID orderId, String refundRef, String reason) {
        DataOrder order = dataOrderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.REFUNDED);
        order.setRefundTxnRef(refundRef);
        order.setFailureReason(reason);
        dataOrderRepository.save(order);
    }

    @Transactional(readOnly = true)
    @Override
    public OffsetPage<DataOrder> getUserOrders(UUID userId, int limit, int offset) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int safeOffset = Math.max(offset, 0);
        var page = dataOrderRepository.findByUserIdOrderByCreatedAtDesc(userId,
                org.springframework.data.domain.PageRequest.of(safeOffset / safeLimit, safeLimit));
        return OffsetPage.of(page.getContent(), page.getTotalElements(), safeLimit, safeOffset);
    }

    @Transactional(readOnly = true)
    @Override
    public DataOrder getOrderById(UUID orderId, UUID userId) {
        return dataOrderRepository.findByIdAndUserId(orderId, userId).orElseThrow(() -> new NotFoundException("Order not found"));
    }
}
