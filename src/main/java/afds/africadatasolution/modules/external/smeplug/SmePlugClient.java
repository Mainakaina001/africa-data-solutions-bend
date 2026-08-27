package afds.africadatasolution.modules.external.smeplug;

import afds.africadatasolution.common.exception.ExternalServiceException;
import afds.africadatasolution.common.exception.FailureClassification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static afds.africadatasolution.common.exception.FailureClassification.AMBIGUOUS;
import static afds.africadatasolution.common.exception.FailureClassification.DEFINITIVE_FAILURE;

/**
 * SME Plug — data/airtime delivery provider. Mirrors backend/src/services/smeplug.service.ts.
 *
 * Only read-only (GET) calls are wrapped in the retry template — purchase
 * calls are non-idempotent and attempted exactly once, matching the
 * original axios-retry config (retries only "get" requests).
 */
@Service
public class SmePlugClient {

    private static final Logger log = LoggerFactory.getLogger(SmePlugClient.class);
    private static final Pattern NIGERIAN_PHONE = Pattern.compile("^(0|\\+234)[789][01]\\d{8}$");
    private static final List<String> MTN_PREFIXES = List.of(
            "0703", "0801", "0706", "0803", "0806", "0810", "0813", "0814", "0816", "0903", "0906");
    private static final Map<String, NetworkInfo> NETWORK_MAP = Map.of(
            "1", new NetworkInfo("MTN", 1),
            "2", new NetworkInfo("GLO", 2),
            "3", new NetworkInfo("9MOBILE", 3),
            "4", new NetworkInfo("AIRTEL", 4));

    private final RestTemplate restTemplate;
    private final RetryTemplate retryTemplate;

    public SmePlugClient(RestTemplate smePlugRestTemplate, RetryTemplate idempotentRetryTemplate) {
        this.restTemplate = smePlugRestTemplate;
        this.retryTemplate = idempotentRetryTemplate;
    }

    public SmePlugDataResponse purchaseData(SmePlugDataRequest request) {
        log.info("Initiating SME Plug data purchase network_id={} plan_id={} phone={}",
                request.network_id(), request.plan_id(), request.phone());
        try {
            SmePlugDataResponse response = restTemplate.postForObject("/data/purchase", request, SmePlugDataResponse.class);
            if (response == null || !response.status()) {
                String msg = response != null ? response.message() : "Data purchase failed";
                throw new ExternalServiceException("SME Plug", msg, DEFINITIVE_FAILURE);
            }
            log.info("SME Plug data purchase successful reference={} phone={}",
                    response.data() != null ? response.data().reference() : null, request.phone());
            return response;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("SME Plug data purchase failed phone={} error={}", request.phone(), e.getMessage());
            throw new ExternalServiceException("SME Plug", extractMessage(e), classify(e));
        }
    }

    public Map<String, Object> queryTransaction(String reference) {
        try {
            SmePlugGenericResponse response = retryTemplate.execute(ctx ->
                    restTemplate.getForObject("/data/query/" + reference, SmePlugGenericResponse.class));
            if (response == null || !response.status()) {
                throw new ExternalServiceException("SME Plug", "Failed to query transaction");
            }
            return response.data();
        } catch (ExternalServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("SME Plug transaction query failed reference={} error={}", reference, e.getMessage());
            throw new ExternalServiceException("SME Plug", extractMessage(e));
        }
    }

    public SmePlugPlansResponse getDataPlans() {
        try {
            SmePlugPlansResponse response = retryTemplate.execute(ctx ->
                    restTemplate.getForObject("/data/plans", SmePlugPlansResponse.class));
            if (response == null || !response.status()) {
                throw new ExternalServiceException("SME Plug", "Failed to fetch data plans");
            }
            return response;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Failed to fetch SME Plug data plans error={}", e.getMessage());
            throw new ExternalServiceException("SME Plug", extractMessage(e));
        }
    }

    /**
     * Live selling price for one plan, straight from SME Plug's own catalog —
     * the reseller dashboard is the source of truth for pricing (never our
     * local data_plans.price), so this is called fresh on every purchase.
     */
    public BigDecimal getLivePrice(int networkId, int planId) {
        SmePlugPlansResponse response = getDataPlans();
        List<SmePlugPlan> networkPlans = response.data().get(String.valueOf(networkId));
        if (networkPlans != null) {
            for (SmePlugPlan plan : networkPlans) {
                if (plan.id() == planId) {
                    return plan.price();
                }
            }
        }
        throw new ExternalServiceException("SME Plug", "Invalid data plan selected", DEFINITIVE_FAILURE);
    }

    public List<NetworkPlans> getFormattedPlans() {
        SmePlugPlansResponse response = getDataPlans();
        List<NetworkPlans> formatted = new ArrayList<>();
        for (Map.Entry<String, List<SmePlugPlan>> entry : response.data().entrySet()) {
            NetworkInfo info = NETWORK_MAP.get(entry.getKey());
            if (info != null) {
                formatted.add(new NetworkPlans(info.name(), info.id(), entry.getValue()));
            }
        }
        return formatted;
    }

    public Map<String, Object> checkBalance() {
        try {
            SmePlugGenericResponse response = retryTemplate.execute(ctx ->
                    restTemplate.getForObject("/account/balance", SmePlugGenericResponse.class));
            if (response == null || !response.status()) {
                throw new ExternalServiceException("SME Plug", "Failed to check balance");
            }
            return response.data();
        } catch (ExternalServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Failed to check SME Plug balance error={}", e.getMessage());
            throw new ExternalServiceException("SME Plug", extractMessage(e));
        }
    }

    public Map<String, String> getNetworks() {
        try {
            SmePlugNetworksResponse response = retryTemplate.execute(ctx ->
                    restTemplate.getForObject("/networks", SmePlugNetworksResponse.class));
            if (response == null || !response.status()) {
                throw new ExternalServiceException("SME Plug", "Failed to fetch networks");
            }
            return response.networks();
        } catch (ExternalServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Failed to fetch SME Plug network list error={}", e.getMessage());
            throw new ExternalServiceException("SME Plug", extractMessage(e));
        }
    }

    public SmePlugDataResponse purchaseAirtime(int networkId, String phone, double amount) {
        log.info("Initiating SME Plug airtime purchase network_id={} phone={} amount={}", networkId, phone, amount);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("network_id", networkId);
            body.put("phone", phone);
            body.put("amount", amount);
            SmePlugDataResponse response = restTemplate.postForObject("/airtime/purchase", body, SmePlugDataResponse.class);
            if (response == null || !response.status()) {
                String msg = response != null ? response.message() : "Airtime purchase failed";
                throw new ExternalServiceException("SME Plug", msg, DEFINITIVE_FAILURE);
            }
            return response;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("SME Plug airtime purchase failed phone={} error={}", phone, e.getMessage());
            throw new ExternalServiceException("SME Plug", extractMessage(e), classify(e));
        }
    }

    public boolean validatePhoneNumber(String phone, String network) {
        String cleanPhone = phone.replaceAll("[\\s-]", "");
        if (!NIGERIAN_PHONE.matcher(cleanPhone).matches()) return false;
        if ("MTN".equals(network)) {
            return MTN_PREFIXES.stream().anyMatch(cleanPhone::startsWith);
        }
        return true;
    }

    public String formatPhoneNumber(String phone) {
        String cleanPhone = phone.replaceAll("[\\s-]", "");
        if (cleanPhone.startsWith("+234")) {
            return "0" + cleanPhone.substring(4);
        } else if (cleanPhone.startsWith("234")) {
            return "0" + cleanPhone.substring(3);
        }
        return cleanPhone;
    }

    /**
     * Classifies a delivery error — DEFINITIVE_FAILURE is safe to refund
     * immediately; AMBIGUOUS means the upstream may have processed the
     * request and reconciliation must requery before refunding.
     */
    private FailureClassification classify(RestClientException e) {
        if (e instanceof ResourceAccessException) return AMBIGUOUS;
        if (e instanceof HttpClientErrorException httpEx) {
            int code = httpEx.getStatusCode().value();
            return (code == 408 || code == 429) ? AMBIGUOUS : DEFINITIVE_FAILURE;
        }
        return AMBIGUOUS;
    }

    private String extractMessage(RestClientException e) {
        if (e instanceof HttpClientErrorException httpEx) {
            String body = httpEx.getResponseBodyAsString();
            if (body != null && !body.isBlank()) return body;
        }
        return e.getMessage();
    }

    private record NetworkInfo(String name, int id) {
    }
}
