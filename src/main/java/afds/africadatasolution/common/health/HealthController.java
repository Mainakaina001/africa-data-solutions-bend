package afds.africadatasolution.common.health;

import afds.africadatasolution.common.config.properties.AppProperties;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** Mirrors the / and /api/v1/health handlers in backend/src/app.ts + backend/src/routes/index.ts. */
@RestController
@SecurityRequirements
public class HealthController {

    private final AppProperties appProperties;
    private final Environment environment;

    public HealthController(AppProperties appProperties, Environment environment) {
        this.appProperties = appProperties;
        this.environment = environment;
    }

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of("success", true, "message", "Welcome to " + appProperties.name() + " API", "version", "1.0.0");
    }

    @GetMapping("/api/v1/health")
    public Map<String, Object> health() {
        return Map.of(
                "success", true,
                "message", appProperties.name() + " API is running",
                "timestamp", Instant.now().toString(),
                "environment", String.join(",", environment.getActiveProfiles()));
    }
}
