package afds.africadatasolution.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Resend transactional email settings. Optional — email sending is skipped (logged) if absent. */
@Validated
@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        String resendApiKey,
        String from
) {
}
