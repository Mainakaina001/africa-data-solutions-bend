package afds.africadatasolution.common.ratelimit;

import afds.africadatasolution.common.response.ApiResponse;
import afds.africadatasolution.common.config.properties.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-IP rate limiting. Mirrors backend/src/middlewares/rateLimiter.ts:
 * a broad global cap on all of {@code /api/v1/**}, plus tighter per-route caps
 * for auth, OTP, payment, purchase, and webhook endpoints.
 *
 * Simplification vs. the original: keyed by IP only (Node additionally mixes
 * in the request-body email for the auth/OTP limiters, keying brute-force
 * protection per-account as well as per-IP). This still blocks the dominant
 * attack shape — many attempts from one source — without needing to buffer
 * and parse the request body ahead of the security/validation layers. It also
 * runs as a single in-memory instance (no Redis-backed shared store), so caps
 * are per-node if this service is horizontally scaled — same fallback Node
 * uses when REDIS_URL isn't configured.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1";
    private static final String WEBHOOK_PREFIX = "/api/v1/webhooks";

    private final RateLimitProperties globalProperties;
    // Deliberately NOT the auto-configured ObjectMapper bean: this Filter is a raw
    // @Component picked up by ServletContextInitializerBeans and constructed while
    // the embedded Tomcat starts inside AbstractApplicationContext#onRefresh(),
    // which runs before finishBeanFactoryInitialization() — JacksonAutoConfiguration's
    // ObjectMapper bean isn't reliably resolvable yet at that point.
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final List<RateLimitPolicy> policies;
    private final RateLimitPolicy globalPolicy;
    private final RateLimitPolicy webhookPolicy;

    public RateLimitFilter(RateLimitProperties globalProperties) {
        this.globalProperties = globalProperties;

        long fifteenMin = 15 * 60 * 1000L;
        long oneMin = 60 * 1000L;

        this.globalPolicy = new RateLimitPolicy("api", null, "/api/v1/**",
                globalProperties.windowMs(), globalProperties.maxRequests(), false,
                "Too many requests, please try again later", "RATE_LIMIT_EXCEEDED");

        this.webhookPolicy = new RateLimitPolicy("webhook", "POST", "/api/v1/webhooks/**", oneMin, 300, false,
                "Too many webhook requests", "WEBHOOK_RATE_LIMIT_EXCEEDED");

        this.policies = List.of(
                new RateLimitPolicy("auth", "POST", "/api/v1/auth/register", fifteenMin, 10, true,
                        "Too many authentication attempts. Please try again later.", "AUTH_RATE_LIMIT_EXCEEDED"),
                new RateLimitPolicy("auth", "POST", "/api/v1/auth/login", fifteenMin, 10, true,
                        "Too many authentication attempts. Please try again later.", "AUTH_RATE_LIMIT_EXCEEDED"),
                new RateLimitPolicy("auth", "POST", "/api/v1/auth/refresh", fifteenMin, 10, true,
                        "Too many authentication attempts. Please try again later.", "AUTH_RATE_LIMIT_EXCEEDED"),
                new RateLimitPolicy("auth", "POST", "/api/v1/auth/change-password", fifteenMin, 10, true,
                        "Too many authentication attempts. Please try again later.", "AUTH_RATE_LIMIT_EXCEEDED"),
                new RateLimitPolicy("auth", "POST", "/api/v1/auth/create-pin", fifteenMin, 10, true,
                        "Too many authentication attempts. Please try again later.", "AUTH_RATE_LIMIT_EXCEEDED"),
                new RateLimitPolicy("auth", "POST", "/api/v1/auth/change-pin", fifteenMin, 10, true,
                        "Too many authentication attempts. Please try again later.", "AUTH_RATE_LIMIT_EXCEEDED"),
                new RateLimitPolicy("auth", "POST", "/api/v1/auth/2fa/disable", fifteenMin, 10, true,
                        "Too many authentication attempts. Please try again later.", "AUTH_RATE_LIMIT_EXCEEDED"),
                new RateLimitPolicy("otp", "POST", "/api/v1/auth/forgot-password", fifteenMin, 5, false,
                        "Too many OTP requests. Please try again later.", "OTP_RATE_LIMIT_EXCEEDED"),
                new RateLimitPolicy("otp", "POST", "/api/v1/auth/reset-password", fifteenMin, 5, false,
                        "Too many OTP requests. Please try again later.", "OTP_RATE_LIMIT_EXCEEDED"),
                new RateLimitPolicy("payment", "POST", "/api/v1/wallet/fund/initiate", oneMin, 10, false,
                        "Too many payment requests, please try again later", "PAYMENT_RATE_LIMIT_EXCEEDED"),
                new RateLimitPolicy("payment", "POST", "/api/v1/bills/electricity/pay", oneMin, 10, false,
                        "Too many payment requests, please try again later", "PAYMENT_RATE_LIMIT_EXCEEDED"),
                new RateLimitPolicy("payment", "POST", "/api/v1/bills/tv/pay", oneMin, 10, false,
                        "Too many payment requests, please try again later", "PAYMENT_RATE_LIMIT_EXCEEDED"),
                new RateLimitPolicy("payment", "POST", "/api/v1/bills/education/pay", oneMin, 10, false,
                        "Too many payment requests, please try again later", "PAYMENT_RATE_LIMIT_EXCEEDED"),
                new RateLimitPolicy("purchase", "POST", "/api/v1/data/buy", oneMin, 5, false,
                        "Too many purchase requests, please try again later", "PURCHASE_RATE_LIMIT_EXCEEDED"),
                new RateLimitPolicy("purchase", "POST", "/api/v1/airtime/purchase", oneMin, 5, false,
                        "Too many purchase requests, please try again later", "PURCHASE_RATE_LIMIT_EXCEEDED")
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith(API_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();

        if (path.startsWith(WEBHOOK_PREFIX)) {
            if (matches(webhookPolicy, request) && !consume(webhookPolicy, ip)) {
                reject(response, webhookPolicy);
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        if (!consume(globalPolicy, ip)) {
            reject(response, globalPolicy);
            return;
        }

        RateLimitPolicy specific = policies.stream().filter(p -> matches(p, request)).findFirst().orElse(null);
        if (specific == null) {
            chain.doFilter(request, response);
            return;
        }

        String key = key(specific, ip);
        WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter(specific.windowMs()));

        if (specific.skipSuccessful()) {
            if (counter.peekExceeds(specific.max())) {
                reject(response, specific);
                return;
            }
            chain.doFilter(request, response);
            if (response.getStatus() >= 400) {
                counter.increment();
            }
            return;
        }

        if (!counter.checkAndIncrement(specific.max())) {
            reject(response, specific);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean matches(RateLimitPolicy policy, HttpServletRequest request) {
        if (policy.method() != null && !policy.method().equalsIgnoreCase(request.getMethod())) return false;
        return pathMatcher.match(policy.pathPattern(), request.getRequestURI());
    }

    private boolean consume(RateLimitPolicy policy, String ip) {
        WindowCounter counter = counters.computeIfAbsent(key(policy, ip), k -> new WindowCounter(policy.windowMs()));
        return counter.checkAndIncrement(policy.max());
    }

    private String key(RateLimitPolicy policy, String ip) {
        return policy.name() + ":" + policy.pathPattern() + ":" + ip;
    }

    private void reject(HttpServletResponse response, RateLimitPolicy policy) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(policy.message(), policy.code(), null));
    }
}
