package afds.africadatasolution.modules.payment;

import afds.africadatasolution.common.response.ApiResponse;
import afds.africadatasolution.common.config.properties.BillstackProperties;
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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Source-IP allowlist guard for the Billstack webhook. Supports plain IPv4/IPv6
 * and CIDR. If the allowlist is empty the check is disabled (dev/test); in
 * production populate BILLSTACK_WEBHOOK_IPS with Billstack's egress range.
 * Mirrors backend/src/middlewares/webhookGuard.ts.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class WebhookIpAllowlistFilter extends OncePerRequestFilter {

    private final List<String> allowlist;
    // See RateLimitFilter for why this isn't the auto-configured ObjectMapper bean.
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public WebhookIpAllowlistFilter(BillstackProperties properties) {
        this.allowlist = properties.webhookIpAllowlist() == null ? List.of() : properties.webhookIpAllowlist();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!request.getRequestURI().startsWith("/api/v1/webhooks") || allowlist.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        boolean allowed = allowlist.stream().anyMatch(entry -> entry.contains("/") ? inCidr(ip, entry) : ip.equals(entry));
        if (!allowed) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.error("Source IP not allowed", "AUTHENTICATION_ERROR", null));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean inCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/", 2);
            if (parts.length != 2) return ip.equals(parts[0]);
            InetAddress target = InetAddress.getByName(ip);
            InetAddress range = InetAddress.getByName(parts[0]);
            int prefixBits = Integer.parseInt(parts[1]);

            byte[] targetBytes = target.getAddress();
            byte[] rangeBytes = range.getAddress();
            if (targetBytes.length != rangeBytes.length) return false;

            int fullBytes = prefixBits / 8;
            int tailBits = prefixBits % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (targetBytes[i] != rangeBytes[i]) return false;
            }
            if (tailBits > 0 && fullBytes < targetBytes.length) {
                int mask = 0xFF << (8 - tailBits);
                if ((targetBytes[fullBytes] & mask) != (rangeBytes[fullBytes] & mask)) return false;
            }
            return true;
        } catch (UnknownHostException | NumberFormatException e) {
            return false;
        }
    }
}
