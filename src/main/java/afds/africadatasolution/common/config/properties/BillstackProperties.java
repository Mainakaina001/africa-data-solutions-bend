package afds.africadatasolution.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Billstack (virtual accounts / wallet funding) provider settings.
 * Mirrors BILLSTACK_* keys in backend/.env.example.
 */
@Validated
@ConfigurationProperties(prefix = "app.billstack")
public record BillstackProperties(
        @NotBlank String apiKey,
        @NotBlank String secretKey,
        @NotBlank String baseUrl,
        @NotBlank String webhookSecret,
        List<String> webhookIpAllowlist
) {
}
