package afds.africadatasolution.modules.auth.service;

import java.util.List;
import java.util.UUID;

/**
 * Two-factor authentication (TOTP). Mirrors backend/src/services/twoFactor.service.ts.
 * Enable flow: setup() -> user scans QR -> verifyAndEnable(code). Until that
 * succeeds, {@code twoFactorEnabled} stays false.
 */
public interface TwoFactorService {

    SetupResult setup(UUID userId);

    List<String> verifyAndEnable(UUID userId, String code);

    /** Verifies a TOTP code, falling back to a single-use backup code. */
    boolean verify(UUID userId, String code);

    void disable(UUID userId);

    record SetupResult(String otpauthUrl, String qrCodeDataUrl) {
    }
}
