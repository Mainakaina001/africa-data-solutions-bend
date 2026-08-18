package afds.africadatasolution.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Top-level application identity and networking settings.
 * Mirrors backend/src/config/env.ts (appName, appUrl, frontendOrigins, trustedProxies).
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String name,
        @NotBlank String url,
        @NotEmpty List<String> frontendOrigins,
        int trustedProxies
) {
}
