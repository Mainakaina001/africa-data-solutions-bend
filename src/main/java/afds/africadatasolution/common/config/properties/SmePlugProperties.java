package afds.africadatasolution.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** SME Plug (data/airtime delivery) provider settings. */
@Validated
@ConfigurationProperties(prefix = "app.sme-plug")
public record SmePlugProperties(
        @NotBlank String apiKey,
        @NotBlank String baseUrl
) {
}
