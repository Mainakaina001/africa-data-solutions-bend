package afds.africadatasolution;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Excludes {@link UserDetailsServiceAutoConfiguration}: this API authenticates
 * exclusively via {@link afds.africadatasolution.common.security.JwtAuthenticationFilter}
 * (stateless JWT, no form-login/basic-auth) and never defines or consults a
 * {@code UserDetailsService}. Without the exclusion, Boot creates an unused
 * in-memory user with a random password and logs it on every startup.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
public class AfricaDataSolutionApplication {

    public static void main(String[] args) {
        SpringApplication.run(AfricaDataSolutionApplication.class, args);
    }

}
