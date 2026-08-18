package afds.africadatasolution.modules.auth.dto.response;

import afds.africadatasolution.modules.payment.dto.response.VirtualAccountSummary;

public record RegisterResponse(
        UserSummary user,
        String accessToken,
        String refreshToken,
        java.time.Instant refreshExpiresAt,
        VirtualAccountSummary virtualAccount
) {
}
