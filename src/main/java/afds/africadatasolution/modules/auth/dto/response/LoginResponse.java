package afds.africadatasolution.modules.auth.dto.response;

import java.time.Instant;

public record LoginResponse(
        boolean twoFactorRequired,
        AuthenticatedUserView user,
        Boolean twoFactorEnabled,
        String accessToken,
        String refreshToken,
        Instant refreshExpiresAt
) {
    public static LoginResponse requiresTwoFactor() {
        return new LoginResponse(true, null, null, null, null, null);
    }

    public static LoginResponse authenticated(AuthenticatedUserView user, boolean twoFactorEnabled, AuthTokens tokens) {
        return new LoginResponse(false, user, twoFactorEnabled, tokens.accessToken(), tokens.refreshToken(), tokens.refreshExpiresAt());
    }
}
