package afds.africadatasolution.common.config.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Global (per-IP) API rate-limit window. Route-specific limits are defined in {@code RateLimitPolicies}. */
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @Positive long windowMs,
        @Positive int maxRequests
) {
}
