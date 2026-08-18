package afds.africadatasolution.common.security;

import afds.africadatasolution.domain.user.User;
import afds.africadatasolution.domain.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Bearer-JWT authentication filter — mirrors backend/src/middlewares/auth.ts.
 *
 * Validates the token signature/issuer/audience, then re-checks the user's
 * live state (active, not locked, tokenVersion still current) on every
 * request so a compromised token dies the instant the account is revoked.
 *
 * On any failure the request is simply left unauthenticated; the reason is
 * stashed in a request attribute so {@link RestAuthenticationEntryPoint} can
 * report the specific cause (matches the Node error messages).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_FAILURE_ATTRIBUTE = "afds.authFailureMessage";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                authenticate(request, token);
            } else {
                request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "No token provided");
            }
        }

        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        JwtService.AccessTokenClaims claims;
        try {
            claims = jwtService.verify(token);
        } catch (JwtService.InvalidTokenException e) {
            request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "Invalid or expired token");
            return;
        }

        Optional<User> maybeUser = userRepository.findById(claims.userId());
        if (maybeUser.isEmpty()) {
            request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "User no longer exists");
            return;
        }
        User user = maybeUser.get();
        if (!user.isActive()) {
            request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "User account is deactivated");
            return;
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "Account temporarily locked");
            return;
        }
        if (user.getTokenVersion() != claims.tokenVersion()) {
            request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "Session no longer valid");
            return;
        }

        AuthUser principal = new AuthUser(user.getId(), user.getEmail(), user.getPhone(), user.getRole());
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        AbstractAuthenticationToken authentication = new AuthUserAuthenticationToken(principal, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
