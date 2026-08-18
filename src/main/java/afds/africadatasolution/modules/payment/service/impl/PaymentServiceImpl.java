package afds.africadatasolution.modules.payment.service.impl;

import afds.africadatasolution.common.exception.AuthenticationException;
import afds.africadatasolution.common.exception.ExternalServiceException;
import afds.africadatasolution.common.exception.NotFoundException;
import afds.africadatasolution.common.exception.ValidationException;
import afds.africadatasolution.common.config.properties.AppProperties;
import afds.africadatasolution.common.config.properties.WalletProperties;
import afds.africadatasolution.domain.user.User;
import afds.africadatasolution.domain.user.UserRepository;
import afds.africadatasolution.domain.virtualaccount.VirtualAccount;
import afds.africadatasolution.domain.virtualaccount.VirtualAccountRepository;
import afds.africadatasolution.domain.wallet.Wallet;
import afds.africadatasolution.domain.webhook.WebhookEvent;
import afds.africadatasolution.domain.webhook.WebhookEventRepository;
import afds.africadatasolution.modules.external.billstack.BillstackClient;
import afds.africadatasolution.modules.external.billstack.BillstackPaymentRequest;
import afds.africadatasolution.modules.external.billstack.BillstackPaymentResponse;
import afds.africadatasolution.modules.external.billstack.BillstackVirtualAccountRequest;
import afds.africadatasolution.modules.external.billstack.BillstackVirtualAccountResponse;
import afds.africadatasolution.modules.outbox.service.OutboxService;
import afds.africadatasolution.modules.payment.dto.response.FundingInitiationResponse;
import afds.africadatasolution.modules.payment.dto.response.VirtualAccountListItem;
import afds.africadatasolution.modules.payment.dto.response.VirtualAccountSummary;
import afds.africadatasolution.modules.payment.service.PaymentService;
import afds.africadatasolution.modules.wallet.WalletCreditCommand;
import afds.africadatasolution.modules.wallet.service.WalletService;
import afds.africadatasolution.modules.wallet.dto.response.WalletTransactionView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    private static final List<String> VALID_BANKS = List.of("PALMPAY", "9PSB", "SAFEHAVEN", "PROVIDUS", "BANKLY");

    private final UserRepository userRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final WalletService walletService;
    private final BillstackClient billstackClient;
    private final OutboxService outboxService;
    private final AppProperties appProperties;
    private final WalletProperties walletProperties;
    private final ObjectMapper objectMapper;

    public PaymentServiceImpl(UserRepository userRepository, VirtualAccountRepository virtualAccountRepository,
                               WebhookEventRepository webhookEventRepository, WalletService walletService,
                               BillstackClient billstackClient, OutboxService outboxService, AppProperties appProperties,
                               WalletProperties walletProperties, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.virtualAccountRepository = virtualAccountRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.walletService = walletService;
        this.billstackClient = billstackClient;
        this.outboxService = outboxService;
        this.appProperties = appProperties;
        this.walletProperties = walletProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @Override
    public CreateVirtualAccountResult createVirtualAccount(UUID userId, String bank) {
        if (!VALID_BANKS.contains(bank)) {
            throw new ValidationException("Bank must be one of: " + String.join(", ", VALID_BANKS));
        }

        var existing = virtualAccountRepository.findFirstByUserIdAndIsActiveTrue(userId);
        if (existing.isPresent()) {
            return new CreateVirtualAccountResult(VirtualAccountSummary.from(existing.get()), true);
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new AuthenticationException("User not found"));

        String reference = "VA_" + userId.toString().substring(0, 8) + "_" + System.currentTimeMillis();
        BillstackVirtualAccountResponse response = billstackClient.generateVirtualAccount(new BillstackVirtualAccountRequest(
                user.getEmail(), reference, user.getFirstName(), user.getLastName(), user.getPhone(), bank));

        var accountDetails = response.data().account().isEmpty() ? null : response.data().account().get(0);
        if (accountDetails == null) {
            throw new ExternalServiceException("Billstack", "No account details returned from Billstack");
        }

        VirtualAccount va = new VirtualAccount();
        va.setUserId(userId);
        va.setAccountReference(reference);
        va.setAccountNumber(accountDetails.accountNumber());
        va.setAccountName(accountDetails.accountName());
        va.setBankName(accountDetails.bankName());
        va.setBankCode(accountDetails.bankId());
        va.setMetadata(Map.of("bank", bank, "createdVia", "api"));
        virtualAccountRepository.save(va);

        return new CreateVirtualAccountResult(VirtualAccountSummary.from(va), false);
    }

    @Transactional(readOnly = true)
    @Override
    public List<VirtualAccountListItem> getVirtualAccounts(UUID userId) {
        return virtualAccountRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(VirtualAccountListItem::from).toList();
    }

    @Transactional
    @Override
    public FundingInitiationResponse initiateFunding(UUID userId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.valueOf(walletProperties.perTxMax())) > 0) {
            throw new ValidationException("Maximum funding amount is ₦" + walletProperties.perTxMax());
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new AuthenticationException("User not found"));

        String reference = "FUND_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        BillstackPaymentResponse response = billstackClient.initializePayment(new BillstackPaymentRequest(
                amount.multiply(BigDecimal.valueOf(100)).longValueExact(), user.getEmail(), reference,
                appProperties.url() + "/wallet/funding-callback", Map.of("userId", userId.toString(), "type", "wallet_funding")));

        return new FundingInitiationResponse(reference, response.data().authorization_url(), response.data().access_code(), amount);
    }

    @Transactional(readOnly = true)
    @Override
    public WalletTransactionView verifyFunding(UUID userId, String reference) {
        try {
            var txn = walletService.getTransactionByReferenceForUser(reference, userId);
            return WalletTransactionView.from(txn);
        } catch (NotFoundException e) {
            throw new NotFoundException("No payment found for this reference");
        }
    }

    // ─── Webhook ────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public WebhookResult handleWebhook(byte[] rawBody, String signatureBillstack, String signatureWiaxy) {
        Map<String, Object> body = parseJson(rawBody);
        Map<String, Object> data = asMap(body.get("data"));
        boolean isReservedAccount = "PAYMENT_NOTIFICATION".equals(body.get("event"))
                && "RESERVED_ACCOUNT_TRANSACTION".equals(data.get("type"));

        boolean signatureValid;
        if (signatureBillstack != null) {
            signatureValid = billstackClient.verifyWebhookSignature(rawBody, signatureBillstack);
        } else if (isReservedAccount) {
            signatureValid = billstackClient.verifyReservedAccountSignature(signatureWiaxy);
        } else {
            signatureValid = false;
        }
        if (!signatureValid) {
            log.warn("Webhook rejected: invalid or missing signature isReservedAccount={}", isReservedAccount);
            throw new AuthenticationException("Invalid webhook signature");
        }

        String externalId = isReservedAccount
                ? firstNonBlank(str(data.get("transaction_ref")), str(data.get("reference")))
                : str(data.get("reference"));
        if (externalId == null) externalId = "unknown_" + System.currentTimeMillis();

        String payloadHash = sha256Hex(rawBody);

        WebhookEvent event = new WebhookEvent();
        event.setProvider("billstack");
        event.setExternalId(externalId);
        event.setPayloadHash(payloadHash);
        event.setStatus("PROCESSING");
        try {
            webhookEventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            log.info("Webhook duplicate (already received) externalId={}", externalId);
            return new WebhookResult(false, true, externalId);
        }

        try {
            if (isReservedAccount) {
                processReservedAccount(data);
            } else {
                processLegacyPayment(body, data);
            }
            webhookEventRepository.updateStatus("billstack", externalId, "PROCESSED", Instant.now());
            return new WebhookResult(true, false, externalId);
        } catch (RuntimeException e) {
            webhookEventRepository.updateStatus("billstack", externalId, "FAILED", null);
            throw e;
        }
    }

    private void processReservedAccount(Map<String, Object> data) {
        String transactionReference = firstNonBlank(str(data.get("transaction_ref")), str(data.get("reference")));
        BigDecimal amount = toBigDecimal(data.get("amount"));
        List<Map<String, Object>> payers = asMapList(data.get("payer"));
        Map<String, Object> payer = payers.isEmpty() ? Map.of() : payers.get(0);
        String accountNumber = str(payer.get("account_number"));
        Map<String, Object> customer = asMap(data.get("customer"));
        String payerName = firstNonBlank(str(payer.get("account_name")),
                (str(customer.get("firstName")) + " " + str(customer.get("lastName"))).trim());

        if (transactionReference == null || accountNumber == null || amount == null) {
            throw new ValidationException("Malformed reserved-account payload");
        }

        VirtualAccount virtualAccount = virtualAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new NotFoundException("Virtual account not found"));
        UUID userId = virtualAccount.getUserId();
        Wallet wallet = walletService.getWalletByUserId(userId);

        walletService.creditWallet(new WalletCreditCommand(wallet.getId(), amount, transactionReference,
                "Wallet funding via virtual account",
                Map.of("accountNumber", virtualAccount.getAccountNumber(), "accountName", virtualAccount.getAccountName(),
                        "bankName", virtualAccount.getBankName(), "payerName", payerName,
                        "transactionType", "reserved_account")));

        outboxService.enqueue("notification.wallet_funded", userId.toString(),
                Map.of("userId", userId.toString(), "amount", amount.toString()));
    }

    private void processLegacyPayment(Map<String, Object> body, Map<String, Object> data) {
        if (!"charge.success".equals(body.get("event")) || !"success".equals(data.get("status"))) {
            log.info("Legacy webhook ignored: not a successful payment event={}", body.get("event"));
            return;
        }
        String reference = str(data.get("reference"));
        BigDecimal amountKobo = toBigDecimal(data.get("amount"));
        if (reference == null || amountKobo == null) {
            throw new ValidationException("Malformed payment payload");
        }
        BigDecimal amount = amountKobo.divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP);

        Map<String, Object> verified = billstackClient.verifyPayment(reference);
        Map<String, Object> metadata = asMap(verified.get("metadata"));
        String userIdStr = str(metadata.get("userId"));
        if (userIdStr == null) throw new ValidationException("Unknown payment metadata");
        UUID userId = UUID.fromString(userIdStr);

        Wallet wallet = walletService.getWalletByUserId(userId);
        walletService.creditWallet(new WalletCreditCommand(wallet.getId(), amount, reference, "Wallet funding via Billstack",
                Map.of("channel", String.valueOf(verified.get("channel")), "paidAt", String.valueOf(verified.get("paid_at")),
                        "billstackReference", reference)));

        outboxService.enqueue("notification.wallet_funded", userId.toString(),
                Map.of("userId", userId.toString(), "amount", amount.toString()));
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(byte[] raw) {
        try {
            return objectMapper.readValue(raw, Map.class);
        } catch (Exception e) {
            throw new ValidationException("Malformed JSON");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object o) {
        return o instanceof List ? (List<Map<String, Object>>) o : List.of();
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
