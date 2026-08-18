package afds.africadatasolution.modules.auth.dto.response;

import java.time.Instant;

public record AuthTokens(String accessToken, String refreshToken, Instant refreshExpiresAt) {
}
