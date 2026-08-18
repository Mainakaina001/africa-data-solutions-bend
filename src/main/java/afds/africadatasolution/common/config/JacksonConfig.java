package afds.africadatasolution.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4's auto-configured mapper is Jackson 3 ({@code tools.jackson.databind.json.JsonMapper},
 * bean name {@code jacksonJsonMapper}) — it no longer provides a bean of the classic
 * {@code com.fasterxml.jackson.databind.ObjectMapper} type. A few classes (webhook HMAC signing,
 * security error handlers) are written against that classic type; jackson-databind 2.x is only on
 * the classpath transitively (jjwt-jackson, springdoc), so without this bean those constructors fail
 * to resolve. Settings mirror application.yml's {@code spring.jackson} block.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
