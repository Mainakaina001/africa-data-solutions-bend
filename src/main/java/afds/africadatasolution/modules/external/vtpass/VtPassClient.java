package afds.africadatasolution.modules.external.vtpass;

import afds.africadatasolution.common.exception.ExternalServiceException;
import afds.africadatasolution.common.exception.FailureClassification;
import afds.africadatasolution.common.config.properties.VtPassProperties;
import afds.africadatasolution.domain.order.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import static afds.africadatasolution.common.exception.FailureClassification.AMBIGUOUS;
import static afds.africadatasolution.common.exception.FailureClassification.DEFINITIVE_FAILURE;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VTPass — electricity / TV / education / airtime provider.
 * Mirrors backend/src/services/vtpass.service.ts.
 */
@Service
public class VtPassClient {

    private static final Logger log = LoggerFactory.getLogger(VtPassClient.class);

    private static final String SUCCESS = "000";
    private static final String PROCESSING = "099";
    private static final Set<String> TERMINAL_FAILURES = Set.of(
            "016", "010", "011", "012", "013", "017", "018", "019",
            "021", "022", "023", "024", "030", "031", "032", "034", "035", "083", "087", "091");

    public static final List<ProviderInfo> ELECTRICITY_PROVIDERS = List.of(
            new ProviderInfo("ikeja-electric", "Ikeja Electric (IKEDC)"),
            new ProviderInfo("eko-electric", "Eko Electric (EKEDC)"),
            new ProviderInfo("kano-electric", "Kano Electric (KEDCO)"),
            new ProviderInfo("phed", "Port Harcourt Electric (PHED)"),
            new ProviderInfo("enugu-electric", "Enugu Electric (EEDC)"),
            new ProviderInfo("abuja-electric", "Abuja Electric (AEDC)"),
            new ProviderInfo("ibadan-electric", "Ibadan Electric (IBEDC)"),
            new ProviderInfo("jos-electric", "Jos Electric (JED)"),
            new ProviderInfo("kaduna-electric", "Kaduna Electric (KAEDCO)"),
            new ProviderInfo("benin-electric", "Benin Electric (BEDC)"));

    public static final List<ProviderInfo> TV_PROVIDERS = List.of(
            new ProviderInfo("dstv", "DSTV"),
            new ProviderInfo("gotv", "GOtv"),
            new ProviderInfo("startimes", "Startimes"));

    public static final List<ProviderInfo> EDUCATION_PROVIDERS = List.of(
            new ProviderInfo("waec", "WAEC Result Checker"),
            new ProviderInfo("waec-registration", "WAEC Registration"),
            new ProviderInfo("jamb", "JAMB (UTME & Direct Entry)"),
            new ProviderInfo("neco", "NECO Result Checker"));

    public static final List<ProviderInfo> AIRTIME_NETWORKS = List.of(
            new ProviderInfo("mtn", "MTN"),
            new ProviderInfo("airtel", "Airtel"),
            new ProviderInfo("etisalat", "9mobile (Etisalat)"),
            new ProviderInfo("glo", "Glo"));

    private final RestTemplate restTemplate;
    private final RetryTemplate retryTemplate;
    private final VtPassProperties properties;

    private final Map<String, CachedVariations> variationCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    public VtPassClient(RestTemplate vtPassRestTemplate, RetryTemplate idempotentRetryTemplate, VtPassProperties properties) {
        this.restTemplate = vtPassRestTemplate;
        this.retryTemplate = idempotentRetryTemplate;
        this.properties = properties;
    }

    /** Request IDs must start with YYYYMMDD per VTPass docs, max 30 chars. */
    public String generateRequestId(UUID userId) {
        String datePrefix = DateTimeFormatter.ofPattern("yyyyMMdd").format(Instant.now().atZone(ZoneOffset.UTC));
        String userPart = userId.toString().replace("-", "").substring(0, 8);
        String ts = String.valueOf(System.currentTimeMillis());
        ts = ts.substring(Math.max(0, ts.length() - 10));
        String combined = datePrefix + userPart + ts;
        return combined.substring(0, Math.min(30, combined.length()));
    }

    public VtPassVariationsResponse getVariations(String serviceId) {
        try {
            String uri = UriComponentsBuilder.fromPath("/service-variations")
                    .queryParam("serviceID", serviceId).toUriString();
            HttpHeaders headers = new HttpHeaders();
            headers.set("public-key", properties.publicKey());
            VtPassVariationsResponse response = retryTemplate.execute(ctx ->
                    restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), VtPassVariationsResponse.class).getBody());

            String desc = response != null ? response.response_description() : null;
            if (!"000".equals(desc) && !"success".equals(desc) && !SUCCESS.equals(desc)) {
                throw new ExternalServiceException("VTPass", "No variations found for service: " + serviceId);
            }
            List<VtPassVariation> variations = response.content() != null ? response.content().variations() : null;
            if (variations == null || variations.isEmpty()) {
                throw new ExternalServiceException("VTPass", "No variations available for: " + serviceId);
            }
            log.info("VTPass variations retrieved serviceID={} count={}", serviceId, variations.size());
            return response;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("VTPass getVariations failed serviceID={} error={}", serviceId, e.getMessage());
            throw new ExternalServiceException("VTPass", e.getMessage());
        }
    }

    /**
     * Canonical fixed-price lookup, cached 10 minutes. Returns null if the
     * variation is unknown or not fixed-price — callers MUST reject those
     * flows rather than trusting a client-supplied amount.
     */
    public java.math.BigDecimal getCanonicalVariationAmount(String serviceId, String variationCode) {
        long now = System.currentTimeMillis();
        CachedVariations cached = variationCache.get(serviceId);
        if (cached == null || now - cached.fetchedAt() > CACHE_TTL_MS) {
            VtPassVariationsResponse fresh = getVariations(serviceId);
            cached = new CachedVariations(now, fresh.content().variations());
            variationCache.put(serviceId, cached);
        }
        for (VtPassVariation v : cached.variations()) {
            if (v.variation_code().equals(variationCode)) {
                if (!"Yes".equals(v.fixedPrice())) return null;
                try {
                    java.math.BigDecimal amt = new java.math.BigDecimal(v.variation_amount());
                    return amt.signum() > 0 ? amt : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    public VtPassVerifyResponse verifyBillersCode(String billersCode, String serviceId, String type) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("billersCode", billersCode);
            body.put("serviceID", serviceId);
            if (type != null) body.put("type", type);

            HttpHeaders headers = new HttpHeaders();
            headers.set("secret-key", properties.secretKey());
            VtPassVerifyResponse response = restTemplate.exchange(
                    "/merchant-verify", HttpMethod.POST, new HttpEntity<>(body, headers), VtPassVerifyResponse.class).getBody();

            String code = response != null ? response.code() : null;
            if (!"000".equals(code) && !"020".equals(code)) {
                throw new ExternalServiceException("VTPass", "Unable to verify — invalid meter/smartcard/profile number");
            }
            return response;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("VTPass verifyBillersCode failed error={}", e.getMessage());
            throw new ExternalServiceException("VTPass", e.getMessage());
        }
    }

    /** Purchase / pay. Attempted exactly once — never retried (non-idempotent, money-moving). */
    public VtPassPurchaseResponse purchase(VtPassPurchaseCommand cmd) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("request_id", cmd.requestId());
            body.put("serviceID", cmd.serviceId());
            body.put("billersCode", cmd.billersCode());
            body.put("variation_code", cmd.variationCode());
            body.put("amount", cmd.amount());
            body.put("phone", cmd.phone());
            if (cmd.subscriptionType() != null) body.put("subscription_type", cmd.subscriptionType());
            if (cmd.quantity() != null) body.put("quantity", cmd.quantity());

            HttpHeaders headers = new HttpHeaders();
            headers.set("secret-key", properties.secretKey());
            VtPassPurchaseResponse response = restTemplate.exchange(
                    "/pay", HttpMethod.POST, new HttpEntity<>(body, headers), VtPassPurchaseResponse.class).getBody();

            checkTerminalFailure(response, cmd.requestId());
            log.info("VTPass purchase response received requestId={} serviceID={} code={}",
                    cmd.requestId(), cmd.serviceId(), response.code());
            return response;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("VTPass purchase failed requestId={} error={}", cmd.requestId(), e.getMessage());
            throw new ExternalServiceException("VTPass", e.getMessage(), classify(e));
        }
    }

    public VtPassPurchaseResponse purchaseAirtime(String requestId, String serviceId, java.math.BigDecimal amount, String phone) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("request_id", requestId);
            body.put("serviceID", serviceId);
            body.put("amount", amount);
            body.put("phone", phone);

            HttpHeaders headers = new HttpHeaders();
            headers.set("secret-key", properties.secretKey());
            VtPassPurchaseResponse response = restTemplate.exchange(
                    "/pay", HttpMethod.POST, new HttpEntity<>(body, headers), VtPassPurchaseResponse.class).getBody();

            checkTerminalFailure(response, requestId);
            return response;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("VTPass airtime purchase failed requestId={} error={}", requestId, e.getMessage());
            throw new ExternalServiceException("VTPass", e.getMessage(), classify(e));
        }
    }

    /** Requery is idempotent — wrapped in the retry template. */
    public VtPassPurchaseResponse requery(String requestId) {
        try {
            Map<String, Object> body = Map.of("request_id", requestId);
            HttpHeaders headers = new HttpHeaders();
            headers.set("secret-key", properties.secretKey());
            return retryTemplate.execute(ctx -> restTemplate.exchange(
                    "/requery", HttpMethod.POST, new HttpEntity<>(body, headers), VtPassPurchaseResponse.class).getBody());
        } catch (RestClientException e) {
            log.error("VTPass requery failed requestId={} error={}", requestId, e.getMessage());
            throw new ExternalServiceException("VTPass", e.getMessage());
        }
    }

    private void checkTerminalFailure(VtPassPurchaseResponse response, String requestId) {
        if (response == null) throw new ExternalServiceException("VTPass", "Empty response", AMBIGUOUS);
        if (TERMINAL_FAILURES.contains(response.code())) {
            throw new ExternalServiceException("VTPass",
                    response.response_description() != null
                            ? response.response_description()
                            : "Transaction failed (code " + response.code() + ")",
                    DEFINITIVE_FAILURE);
        }
        if (!SUCCESS.equals(response.code()) && !PROCESSING.equals(response.code())) {
            log.warn("VTPass unknown response code — treating as pending requestId={} code={}", requestId, response.code());
        }
    }

    /**
     * Classifies a delivery error — DEFINITIVE_FAILURE is safe to refund
     * immediately; AMBIGUOUS means the upstream may have processed the
     * request and reconciliation must requery before refunding. (The
     * original Node airtime controller conflated "any ExternalServiceError"
     * with "terminal", which meant network timeouts also triggered an
     * immediate refund — risking a double payout if VTPass had actually
     * processed the request. This classification closes that gap.)
     */
    private FailureClassification classify(RestClientException e) {
        if (e instanceof ResourceAccessException) return AMBIGUOUS;
        if (e instanceof HttpClientErrorException httpEx) {
            int code = httpEx.getStatusCode().value();
            return (code == 408 || code == 429) ? AMBIGUOUS : DEFINITIVE_FAILURE;
        }
        return AMBIGUOUS;
    }

    /** Extracts the token/pin from a purchase response across all service types. */
    public String extractPurchasedToken(VtPassPurchaseResponse response) {
        if (response.token() != null) return response.token();
        if (response.Pin() != null) return response.Pin();
        if (response.purchased_code() != null && !response.purchased_code().isBlank()) return response.purchased_code();
        if (response.tokens() != null && !response.tokens().isEmpty()) return String.join(", ", response.tokens());
        return null;
    }

    /** Maps a VTPass response code + transaction status to our {@link OrderStatus}. */
    public OrderStatus resolveOrderStatus(String code, String txStatus) {
        if (TERMINAL_FAILURES.contains(code)) return OrderStatus.FAILED;
        if (PROCESSING.equals(code)) return OrderStatus.PROCESSING;
        if (SUCCESS.equals(code)) {
            if (txStatus == null || "delivered".equals(txStatus)) return OrderStatus.COMPLETED;
            if ("pending".equals(txStatus) || "initiated".equals(txStatus)) return OrderStatus.PROCESSING;
        }
        return OrderStatus.PROCESSING;
    }

    private record CachedVariations(long fetchedAt, List<VtPassVariation> variations) {
    }
}
