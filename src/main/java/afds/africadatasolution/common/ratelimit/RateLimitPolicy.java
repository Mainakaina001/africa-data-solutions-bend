package afds.africadatasolution.common.ratelimit;

/**
 * One named rate-limit rule. {@code method} is null to match any HTTP method.
 * {@code skipSuccessful} mirrors express-rate-limit's {@code skipSuccessfulRequests}
 * (used for the auth limiter — only failed attempts count toward the cap, so a
 * legitimate user retyping a password isn't punished for eventually succeeding).
 */
public record RateLimitPolicy(String name, String method, String pathPattern, long windowMs, int max,
                               boolean skipSuccessful, String message, String code) {
}
