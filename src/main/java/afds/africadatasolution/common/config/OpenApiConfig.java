package afds.africadatasolution.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mirrors backend/src/config/swagger.ts.
 *
 * {@code bearerAuth} is applied as a default requirement on every operation
 * (matching SecurityConfig's deny-by-default posture) so the Swagger UI
 * "Authorize" token is sent on every request. Endpoints that are actually
 * public per SecurityConfig's permitAll list opt out individually with
 * {@code @SecurityRequirements} (empty).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI africaDataSolutionOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Africa Data Solutions API")
                        .version("1.0.0")
                        .description("API documentation for Africa Data Solutions - A comprehensive platform "
                                + "for data purchases, wallet management, and virtual account services")
                        .contact(new Contact().name("API Support").email("support@africadatasolutions.com"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter your JWT token in the format: Bearer <token>")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
