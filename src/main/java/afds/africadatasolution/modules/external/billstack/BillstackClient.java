package afds.africadatasolution.modules.external.billstack;

import afds.africadatasolution.common.exception.ExternalServiceException;
import afds.africadatasolution.common.config.properties.BillstackProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Billstack — virtual accounts (wallet funding) provider.
 * Mirrors backend/src/services/billstack.service.ts.
 *
 * Purchase/account-creation POSTs are attempted exactly once (not idempotent
 * — retrying risks creating duplicate resources upstream); the read-only
 * verify call is wrapped in the shared exponential-backoff retry template.
 */
@Service
public class BillstackClient {

    private static final Logger log = LoggerFactory.getLogger(BillstackClient.class);

    private final RestTemplate restTemplate;
    private final RetryTemplate retryTemplate;
    private final BillstackProperties properties;

    public BillstackClient(RestTemplate billstackRestTemplate,
                            RetryTemplate idempotentRetryTemplate,
                            BillstackProperties properties) {
        this.restTemplate = billstackRestTemplate;
        this.retryTemplate = idempotentRetryTemplate;
        this.properties = properties;
    }

    public BillstackVirtualAccountResponse generateVirtualAccount(BillstackVirtualAccountRequest request) {
        log.info("Generating Billstack virtual account reference={} email={} bank={}",
                request.reference(), request.email(), request.bank());
        try {
            BillstackVirtualAccountResponse response =
                    restTemplate.postForObject("/generateVirtualAccount/", request, BillstackVirtualAccountResponse.class);

            if (response == null || !response.status()) {
                String msg = response != null ? response.message() : "Virtual account generation failed";
                throw new ExternalServiceException("Billstack", msg);
            }
            log.info("Billstack virtual account generated reference={}", request.reference());
            return response;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Billstack virtual account generation failed reference={} error={}", request.reference(), e.getMessage());
            throw new ExternalServiceException("Billstack", "Failed to generate virtual account");
        }
    }

    public BillstackPaymentResponse initializePayment(BillstackPaymentRequest request) {
        log.info("Initializing Billstack payment reference={} amount={}", request.reference(), request.amount());
        try {
            BillstackPaymentResponse response =
                    restTemplate.postForObject("/transaction/initialize", request, BillstackPaymentResponse.class);
            if (response == null || !response.status()) {
                String msg = response != null ? response.message() : "Payment initialization failed";
                throw new ExternalServiceException("Billstack", msg);
            }
            return response;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Billstack payment initialization failed reference={} error={}", request.reference(), e.getMessage());
            throw new ExternalServiceException("Billstack", "Failed to initialize payment");
        }
    }

    public java.util.Map<String, Object> verifyPayment(String reference) {
        try {
            BillstackVerifyResponse response = retryTemplate.execute(ctx ->
                    restTemplate.getForObject("/transaction/verify/" + reference, BillstackVerifyResponse.class));
            if (response == null || !response.status()) {
                String msg = response != null ? response.message() : "Payment verification failed";
                throw new ExternalServiceException("Billstack", msg);
            }
            return response.data();
        } catch (ExternalServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Billstack payment verification failed reference={} error={}", reference, e.getMessage());
            throw new ExternalServiceException("Billstack", "Failed to verify payment");
        }
    }

    /** HMAC-SHA512 over the RAW request body, constant-time compared. */
    public boolean verifyWebhookSignature(byte[] rawBody, String signature) {
        if (signature == null || signature.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] expected = mac.doFinal(rawBody);
            byte[] provided = HexFormat.of().parseHex(signature.replaceFirst("^sha512=", ""));
            return MessageDigest.isEqual(expected, provided);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reserved-account channel signature: Billstack's documented format here
     * is md5(secret) — a shared bearer token, not a true HMAC. Caller MUST
     * also enforce the source-IP allowlist for this channel.
     */
    public boolean verifyReservedAccountSignature(String signature) {
        if (signature == null || signature.isBlank()) return false;
        try {
            byte[] provided = HexFormat.of().parseHex(signature);
            boolean valid = matchesMd5(properties.webhookSecret(), provided);
            if (!valid) {
                // TEMPORARY diagnostic for the BILLSTACK_WEBHOOK_SECRET misconfiguration —
                // logs only match/no-match booleans, never secret material. Remove once
                // the correct source property is confirmed and BILLSTACK_WEBHOOK_SECRET
                // is fixed to match it.
                log.warn("Reserved-account signature mismatch: matchesApiKey={} matchesSecretKey={}",
                        matchesMd5(properties.apiKey(), provided), matchesMd5(properties.secretKey(), provided));
            }
            return valid;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean matchesMd5(String candidateSecret, byte[] provided) {
        if (candidateSecret == null || candidateSecret.isBlank()) return false;
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            return MessageDigest.isEqual(md5.digest(candidateSecret.getBytes(StandardCharsets.UTF_8)), provided);
        } catch (Exception e) {
            return false;
        }
    }
}
