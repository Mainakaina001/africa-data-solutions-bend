package afds.africadatasolution.common.config;

import afds.africadatasolution.common.config.properties.BillstackProperties;
import afds.africadatasolution.common.config.properties.SmePlugProperties;
import afds.africadatasolution.common.config.properties.VtPassProperties;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * One {@link RestTemplate} bean per external provider, each with the same
 * timeouts and default headers used by the original axios instances
 * (backend/src/services/{billstack,smeplug,vtpass}.service.ts). Retry with
 * exponential backoff is layered on top via {@link RetryConfig}'s
 * RetryTemplate, applied only to idempotent (GET) calls — see the per-client
 * services for why purchase/pay POSTs are never retried at the HTTP layer.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate billstackRestTemplate(RestTemplateBuilder builder, BillstackProperties props) {
        return builder
                .rootUri(props.baseUrl())
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .additionalInterceptors(bearerAuthInterceptor(props.apiKey()))
                .requestFactory(() -> requestFactory(10_000, 30_000))
                .build();
    }

    @Bean
    public RestTemplate smePlugRestTemplate(RestTemplateBuilder builder, SmePlugProperties props) {
        return builder
                .rootUri(props.baseUrl())
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(60))
                .additionalInterceptors(bearerAuthInterceptor(props.apiKey()))
                .requestFactory(() -> requestFactory(15_000, 60_000))
                .build();
    }

    @Bean
    public RestTemplate vtPassRestTemplate(RestTemplateBuilder builder, VtPassProperties props) {
        return builder
                .rootUri(props.baseUrl())
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(60))
                .defaultHeader("api-key", props.apiKey())
                .requestFactory(() -> requestFactory(15_000, 60_000))
                .build();
    }

    /** Plain RestTemplate for calling Resend's HTTP API (auth header is per-request via Bearer key). */
    @Bean
    public RestTemplate resendRestTemplate(RestTemplateBuilder builder) {
        return builder
                .rootUri("https://api.resend.com")
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(20))
                .requestFactory(() -> requestFactory(10_000, 20_000))
                .build();
    }

    private ClientHttpRequestInterceptor bearerAuthInterceptor(String apiKey) {
        String header = "Bearer " + apiKey;
        return (request, body, execution) -> {
            request.getHeaders().set("Authorization", header);
            return execution.execute(request, body);
        };
    }

    private SimpleClientHttpRequestFactory requestFactory(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return factory;
    }
}
