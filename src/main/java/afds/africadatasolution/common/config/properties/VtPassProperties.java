package afds.africadatasolution.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** VTPass (electricity / TV / education / airtime) provider settings. */
@Validated
@ConfigurationProperties(prefix = "app.vtpass")
public record VtPassProperties(
        String apiKey,
        String publicKey,
        String secretKey,
        String baseUrl
) {
}
