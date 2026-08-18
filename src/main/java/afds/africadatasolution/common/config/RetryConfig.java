package afds.africadatasolution.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

/**
 * Retry-with-exponential-backoff for RestTemplate calls to external providers.
 *
 * Mirrors backend/src/services/smeplug.service.ts's axios-retry setup
 * (retries: 2, exponentialDelay, network/idempotent errors only) — retried
 * only on transient network failures and 5xx responses, NEVER on 4xx
 * (those are definitive provider rejections, not worth retrying) and NEVER
 * wrapped around purchase/pay POSTs (retrying a non-idempotent money-moving
 * call risks double-charging upstream; those calls are attempted exactly
 * once and any ambiguous outcome is left for the reconciliation worker).
 */
@Configuration
public class RetryConfig {

    @Bean
    public RetryTemplate idempotentRetryTemplate() {
        RetryTemplate template = new RetryTemplate();

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                3,
                Map.of(
                        ResourceAccessException.class, true,
                        HttpServerErrorException.class, true
                ),
                true
        );
        template.setRetryPolicy(retryPolicy);

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(500L);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(5_000L);
        template.setBackOffPolicy(backOffPolicy);

        return template;
    }
}
