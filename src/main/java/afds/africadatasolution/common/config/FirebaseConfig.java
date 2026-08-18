package afds.africadatasolution.common.config;

import afds.africadatasolution.common.config.properties.FirebaseProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Firebase Admin SDK bootstrap for FCM push notifications. Mirrors
 * backend/src/config/firebase.ts — push is simply disabled (never a startup
 * failure) if no credentials are configured.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    public FirebaseApp firebaseApp(FirebaseProperties properties) {
        try {
            Path serviceAccountPath = Path.of(properties.serviceAccountPath());
            GoogleCredentials credentials;
            String projectId = properties.projectId();

            if (Files.exists(serviceAccountPath)) {
                try (FileInputStream in = new FileInputStream(serviceAccountPath.toFile())) {
                    credentials = GoogleCredentials.fromStream(in);
                }
                log.info("Firebase initialized with service account file");
            } else if (isNotBlank(properties.projectId()) && isNotBlank(properties.privateKey()) && isNotBlank(properties.clientEmail())) {
                String privateKey = properties.privateKey().replace("\\n", "\n");
                credentials = ServiceAccountCredentials.fromPkcs8(null, properties.clientEmail(), privateKey, null, null);
                log.info("Firebase initialized with environment variables");
            } else {
                log.warn("Firebase not configured. Push notifications will be disabled. "
                        + "Provide FIREBASE_SERVICE_ACCOUNT_PATH or FIREBASE_* credentials.");
                return null;
            }

            FirebaseOptions.Builder options = FirebaseOptions.builder().setCredentials(credentials);
            if (isNotBlank(projectId)) options.setProjectId(projectId);
            return FirebaseApp.initializeApp(options.build());
        } catch (Exception e) {
            log.error("Failed to initialize Firebase: {}", e.getMessage());
            log.warn("Push notifications will be disabled");
            return null;
        }
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
