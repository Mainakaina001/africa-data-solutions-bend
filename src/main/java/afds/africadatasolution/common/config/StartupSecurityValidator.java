package afds.africadatasolution.common.config;

import afds.africadatasolution.common.config.properties.BillstackProperties;
import afds.africadatasolution.common.config.properties.JwtProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Refuses to start if secrets are missing or obviously weak.
 *
 * Mirrors backend/src/config/env.ts#validateEnv — secrets are mandatory in
 * every environment; {@code @NotBlank} on the properties records already
 * enforces presence, this adds the "not a known weak value" check that Bean
 * Validation can't express declaratively.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StartupSecurityValidator implements ApplicationRunner {

    private static final Set<String> BANNED = Set.of(
            "dev-secret-key", "changeme", "secret", "password",
            "12345678", "replace-me", "replace_me"
    );

    private final JwtProperties jwtProperties;
    private final BillstackProperties billstackProperties;

    public StartupSecurityValidator(JwtProperties jwtProperties, BillstackProperties billstackProperties) {
        this.jwtProperties = jwtProperties;
        this.billstackProperties = billstackProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        requireStrong("app.jwt.secret", jwtProperties.secret());
        requireStrong("app.billstack.webhook-secret", billstackProperties.webhookSecret());
        // PaymentPoint issues a single account secret key (no separate webhook
        // secret) that we don't control the format of, so it's only required
        // to be present (see PaymentPointProperties' @NotBlank), not "strong".
    }

    private void requireStrong(String key, String value) {
        if (isWeak(value)) {
            throw new IllegalStateException(
                    key + " must be at least 32 characters and not a known weak value");
        }
    }

    private boolean isWeak(String value) {
        if (value == null || value.length() < 32) return true;
        return BANNED.contains(value.toLowerCase(Locale.ROOT));
    }
}
