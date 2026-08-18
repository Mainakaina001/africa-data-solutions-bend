package afds.africadatasolution.common.config.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Velocity / fraud-control settings for wallet debits and PIN/login lockouts.
 * Mirrors the WALLET_*, PIN_*, LOGIN_* keys in backend/.env.example.
 */
@Validated
@ConfigurationProperties(prefix = "app.wallet")
public record WalletProperties(
        @Positive long dailyDebitCap,
        @Positive long perTxMax,
        boolean strictLocking,
        @Positive int pinMaxFailedAttempts,
        @Positive int loginMaxFailedAttempts,
        @Positive int loginLockoutMinutes
) {
}
