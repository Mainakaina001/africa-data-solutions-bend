package afds.africadatasolution.modules.bills.service.impl;

import afds.africadatasolution.common.exception.ExternalServiceException;
import afds.africadatasolution.common.exception.FailureClassification;
import afds.africadatasolution.common.exception.InsufficientBalanceException;
import afds.africadatasolution.common.exception.NotFoundException;
import afds.africadatasolution.common.exception.ValidationException;
import afds.africadatasolution.common.response.NumberedPage;
import afds.africadatasolution.common.config.properties.WalletProperties;
import afds.africadatasolution.domain.order.BillCategory;
import afds.africadatasolution.domain.order.BillPayment;
import afds.africadatasolution.domain.order.BillPaymentRepository;
import afds.africadatasolution.domain.order.OrderStatus;
import afds.africadatasolution.domain.wallet.Wallet;
import afds.africadatasolution.modules.bills.service.BillsService;
import afds.africadatasolution.modules.external.vtpass.*;
import afds.africadatasolution.modules.outbox.service.OutboxService;
import afds.africadatasolution.modules.wallet.WalletCreditCommand;
import afds.africadatasolution.modules.wallet.WalletDebitCommand;
import afds.africadatasolution.modules.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class BillsServiceImpl implements BillsService {

    private static final Logger log = LoggerFactory.getLogger(BillsServiceImpl.class);

    private final BillPaymentRepository billPaymentRepository;
    private final WalletService walletService;
    private final VtPassClient vtPassClient;
    private final OutboxService outboxService;
    private final WalletProperties walletProperties;

    public BillsServiceImpl(BillPaymentRepository billPaymentRepository, WalletService walletService, VtPassClient vtPassClient,
                             OutboxService outboxService, WalletProperties walletProperties) {
        this.billPaymentRepository = billPaymentRepository;
        this.walletService = walletService;
        this.vtPassClient = vtPassClient;
        this.outboxService = outboxService;
        this.walletProperties = walletProperties;
    }

    @Override
    public VtPassVariationsResponse getVariations(String serviceId) {
        return vtPassClient.getVariations(serviceId);
    }

    @Override
    public VtPassVerifyResponse verifyMeter(String meterNumber, String serviceId, String type) {
        return vtPassClient.verifyBillersCode(meterNumber, serviceId, type != null ? type : "prepaid");
    }

    @Override
    public VtPassVerifyResponse verifySmartcard(String smartcardNumber, String serviceId) {
        return vtPassClient.verifyBillersCode(smartcardNumber, serviceId, null);
    }

    @Override
    public VtPassVerifyResponse verifyJambProfile(String profileId, String variationCode) {
        return vtPassClient.verifyBillersCode(profileId, "jamb", variationCode);
    }

    @Override
    public BillPaymentResult payElectricity(UUID userId, String meterNumber, String serviceId, String variationCode,
                                             BigDecimal amount, String phone) {
        return processBillPayment(new ProcessParams(userId, BillCategory.ELECTRICITY, serviceId, meterNumber, variationCode,
                amount, false, phone, null, null,
                "Electricity payment — " + serviceId + " (" + variationCode + ")"));
    }

    @Override
    public BillPaymentResult payTv(UUID userId, String smartcardNumber, String serviceId, String variationCode,
                                    BigDecimal amount, String phone, String subscriptionType) {
        return processBillPayment(new ProcessParams(userId, BillCategory.TV, serviceId, smartcardNumber, variationCode,
                amount, true, phone, null, subscriptionType != null ? subscriptionType : "change",
                "TV subscription — " + serviceId + " (" + variationCode + ")"));
    }

    @Override
    public BillPaymentResult payEducation(UUID userId, String serviceId, String variationCode, BigDecimal amount,
                                           String phone, int quantity) {
        return processBillPayment(new ProcessParams(userId, BillCategory.EDUCATION, serviceId, phone, variationCode,
                amount, true, phone, quantity, null,
                "Education payment — " + serviceId + " (" + variationCode + ")"));
    }

    private record ProcessParams(UUID userId, BillCategory category, String serviceId, String billersCode,
                                  String variationCode, BigDecimal requestedAmount, boolean requireCanonicalPrice,
                                  String phone, Integer quantity, String subscriptionType, String description) {
    }

    private BillPaymentResult processBillPayment(ProcessParams p) {
        if (p.requestedAmount().compareTo(BigDecimal.valueOf(walletProperties.perTxMax())) > 0) {
            throw new ValidationException("Amount exceeds per-transaction limit of ₦" + walletProperties.perTxMax());
        }

        BigDecimal chargeAmount = p.requestedAmount();
        if (p.requireCanonicalPrice()) {
            BigDecimal canonical = vtPassClient.getCanonicalVariationAmount(p.serviceId(), p.variationCode());
            if (canonical == null) {
                throw new ValidationException("This variation is not available or not fixed-price");
            }
            int quantity = p.quantity() != null ? p.quantity() : 1;
            chargeAmount = canonical.multiply(BigDecimal.valueOf(quantity));
            if (chargeAmount.subtract(p.requestedAmount()).abs().compareTo(BigDecimal.valueOf(0.5)) > 0) {
                log.warn("Client amount differs from canonical variation price serviceID={} variationCode={} client={} canonical={}",
                        p.serviceId(), p.variationCode(), p.requestedAmount(), chargeAmount);
            }
        }

        Wallet wallet = walletService.getWalletByUserId(p.userId());
        if (wallet.getBalance().compareTo(chargeAmount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. You need ₦" + chargeAmount + " but have ₦" + wallet.getBalance());
        }

        String reference = vtPassClient.generateRequestId(p.userId());
        String walletTxnRef = "WALLET_BILL_" + reference;

        BillPayment billPayment = createPendingPayment(p, chargeAmount, reference);

        try {
            walletService.debitWallet(new WalletDebitCommand(wallet.getId(), chargeAmount, walletTxnRef, p.description(),
                    Map.of("serviceID", p.serviceId(), "billersCode", p.billersCode(), "variationCode", p.variationCode(),
                            "category", p.category().name(), "billPaymentId", billPayment.getId().toString())));
        } catch (RuntimeException e) {
            markFailed(billPayment.getId(), e.getMessage(), null);
            throw e;
        }
        markProcessing(billPayment.getId(), walletTxnRef);

        try {
            VtPassPurchaseResponse result = vtPassClient.purchase(new VtPassPurchaseCommand(reference, p.serviceId(),
                    p.billersCode(), p.variationCode(), chargeAmount, p.phone(), p.subscriptionType(), p.quantity()));

            String purchasedToken = vtPassClient.extractPurchasedToken(result);
            OrderStatus status = vtPassClient.resolveOrderStatus(result.code(), result.transactionStatus());
            completePayment(billPayment.getId(), status, result, purchasedToken);

            log.info("Bill payment processed userId={} reference={} category={} serviceID={} status={}",
                    p.userId(), reference, p.category(), p.serviceId(), status);

            return new BillPaymentResult(reference, p.serviceId(), p.variationCode(), chargeAmount, status,
                    purchasedToken, result.transactionId());
        } catch (ExternalServiceException error) {
            return handleDeliveryFailure(billPayment, wallet, p.category(), reference, chargeAmount, error);
        }
    }

    private BillPayment createPendingPayment(ProcessParams p, BigDecimal chargeAmount, String reference) {
        BillPayment payment = new BillPayment();
        payment.setUserId(p.userId());
        payment.setCategory(p.category());
        payment.setServiceId(p.serviceId());
        payment.setVariationCode(p.variationCode());
        payment.setBillersCode(p.billersCode());
        payment.setPhone(p.phone());
        payment.setAmount(chargeAmount);
        payment.setReference(reference);
        payment.setStatus(OrderStatus.PENDING);
        return billPaymentRepository.save(payment);
    }

    private void markFailed(UUID id, String reason, String refundTxnRef) {
        BillPayment payment = billPaymentRepository.findById(id).orElseThrow();
        payment.setStatus(OrderStatus.FAILED);
        payment.setFailureReason(reason);
        if (refundTxnRef != null) payment.setRefundTxnRef(refundTxnRef);
        billPaymentRepository.save(payment);
    }

    private void markProcessing(UUID id, String walletTxnRef) {
        BillPayment payment = billPaymentRepository.findById(id).orElseThrow();
        payment.setWalletTxnRef(walletTxnRef);
        payment.setStatus(OrderStatus.PROCESSING);
        billPaymentRepository.save(payment);
    }

    private void completePayment(UUID id, OrderStatus status, VtPassPurchaseResponse result, String purchasedToken) {
        BillPayment payment = billPaymentRepository.findById(id).orElseThrow();
        payment.setStatus(status);
        payment.setVtpassResponse(Map.of("code", String.valueOf(result.code()), "status", String.valueOf(result.transactionStatus())));
        payment.setPurchasedToken(purchasedToken);
        payment.setDeliveredAt(status == OrderStatus.COMPLETED ? Instant.now() : null);
        billPaymentRepository.save(payment);
    }

    private BillPaymentResult handleDeliveryFailure(BillPayment payment, Wallet wallet, BillCategory category,
                                                      String reference, BigDecimal chargeAmount, ExternalServiceException error) {
        log.warn("Bill payment delivery error userId={} reference={} error={} classification={}",
                payment.getUserId(), reference, error.getMessage(), error.getClassification());

        if (error.getClassification() == FailureClassification.DEFINITIVE_FAILURE) {
            String refundTxnRef = "REFUND_BILL_" + reference;
            walletService.creditWallet(new WalletCreditCommand(wallet.getId(), chargeAmount, refundTxnRef,
                    "Refund for failed " + category.name().toLowerCase() + " payment (" + reference + ")",
                    Map.of("originalReference", reference, "reason", "purchase_failed")));
            markFailed(payment.getId(), error.getMessage(), refundTxnRef);
            throw error;
        }

        outboxService.enqueue("reconcile.bill_payment", payment.getId().toString(),
                Map.of("reference", reference, "serviceID", payment.getServiceId(), "category", category.name()));
        throw new ValidationException("Payment is taking longer than expected. You will be notified once it completes; "
                + "if it fails your wallet will be refunded.");
    }

    @Transactional(readOnly = true)
    @Override
    public NumberedPage<BillPayment> getHistory(UUID userId, BillCategory category, int page, int limit) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.min(100, Math.max(1, limit));
        var pageable = PageRequest.of(safePage - 1, safeLimit);
        var result = category != null
                ? billPaymentRepository.findByUserIdAndCategoryOrderByCreatedAtDesc(userId, category, pageable)
                : billPaymentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return NumberedPage.of(result.getContent(), safePage, safeLimit, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    @Override
    public BillPayment getByReference(UUID userId, String reference) {
        return billPaymentRepository.findByReferenceAndUserId(reference, userId)
                .orElseThrow(() -> new NotFoundException("Bill payment not found"));
    }
}
