package afds.africadatasolution.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * PaymentPoint virtual account provider settings — the successor to Billstack
 * for newly-issued accounts (see PaymentPointClient). PaymentPoint issues a
 * single secret key, used both as the API Bearer credential and as the HMAC
 * key for webhook signature verification — there is no separate webhook secret.
 */
@Validated
@ConfigurationProperties(prefix = "app.payment-point")
public record PaymentPointProperties(
        @NotBlank String apiKey,
        @NotBlank String apiSecret,
        @NotBlank String businessId,
        @NotBlank String baseUrl
) {
}
