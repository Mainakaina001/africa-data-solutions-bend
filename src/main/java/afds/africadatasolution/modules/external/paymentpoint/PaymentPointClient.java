package afds.africadatasolution.modules.external.paymentpoint;

import afds.africadatasolution.common.config.properties.PaymentPointProperties;
import afds.africadatasolution.common.exception.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * PaymentPoint — virtual accounts (wallet funding) provider, replacing
 * Billstack for newly-issued accounts. Existing Billstack virtual accounts
 * keep working via the legacy webhook path (see BillstackClient) until every
 * customer has migrated to a PaymentPoint account number.
 */
@Service
public class PaymentPointClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentPointClient.class);

    /** PaymentPoint only supports these two partner banks for virtual accounts. */
    public static final Map<String, String> BANK_CODES = Map.of("PALMPAY", "20946", "OPAY", "20897");

    private final RestTemplate restTemplate;
    private final PaymentPointProperties properties;

    public PaymentPointClient(RestTemplate paymentPointRestTemplate, PaymentPointProperties properties) {
        this.restTemplate = paymentPointRestTemplate;
        this.properties = properties;
    }

    public PaymentPointVirtualAccountResponse generateVirtualAccount(String email, String name, String phone, String bank) {
        String bankCode = BANK_CODES.get(bank);
        if (bankCode == null) {
            throw new IllegalArgumentException("Unsupported bank: " + bank);
        }
        log.info("Generating PaymentPoint virtual account email={} bank={}", email, bank);
        try {
            var request = new PaymentPointVirtualAccountRequest(email, name, phone, List.of(bankCode), properties.businessId());
            PaymentPointVirtualAccountResponse response =
                    restTemplate.postForObject("/api/v1/createVirtualAccount", request, PaymentPointVirtualAccountResponse.class);

            if (response == null || !"success".equalsIgnoreCase(response.status())
                    || response.bankAccounts() == null || response.bankAccounts().isEmpty()) {
                String msg = response != null ? response.message() : "Virtual account generation failed";
                throw new ExternalServiceException("PaymentPoint", msg);
            }
            log.info("PaymentPoint virtual account generated email={}", email);
            return response;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("PaymentPoint virtual account generation failed email={} error={}", email, e.getMessage());
            throw new ExternalServiceException("PaymentPoint", "Failed to generate virtual account");
        }
    }

    /**
     * HMAC-SHA256 over the RAW request body, keyed with the account's secret
     * key (PaymentPoint has no separate webhook secret), constant-time compared.
     */
    public boolean verifyWebhookSignature(byte[] rawBody, String signature) {
        if (signature == null || signature.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.apiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody);
            byte[] provided = HexFormat.of().parseHex(signature);
            return MessageDigest.isEqual(expected, provided);
        } catch (Exception e) {
            return false;
        }
    }
}
