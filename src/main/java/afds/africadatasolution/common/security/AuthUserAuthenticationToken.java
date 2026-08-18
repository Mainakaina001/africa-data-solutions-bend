package afds.africadatasolution.common.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/** Authentication token carrying an {@link AuthUser} as its principal (no credentials post-verification). */
public class AuthUserAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthUser principal;

    public AuthUserAuthenticationToken(AuthUser principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public AuthUser getPrincipal() {
        return principal;
    }
}
