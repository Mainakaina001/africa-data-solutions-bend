package afds.africadatasolution.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Firebase Admin SDK settings for FCM push notifications. All optional — push is disabled if absent. */
@Validated
@ConfigurationProperties(prefix = "app.firebase")
public record FirebaseProperties(
        String projectId,
        String privateKey,
        String clientEmail,
        String serviceAccountPath
) {
}
