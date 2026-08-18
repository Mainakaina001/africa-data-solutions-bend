package afds.africadatasolution.modules.auth.dto.response;

import afds.africadatasolution.domain.user.UserRole;
import afds.africadatasolution.modules.payment.dto.response.VirtualAccountSummary;
import afds.africadatasolution.modules.wallet.dto.response.WalletSummary;

import java.time.Instant;
import java.util.UUID;

public record ProfileResponse(
        UUID id, String email, String phone, String firstName, String lastName,
        UserRole role, boolean isActive, boolean isVerified, boolean twoFactorEnabled,
        Instant createdAt, WalletSummary wallet, VirtualAccountSummary virtualAccount
) {
}
