package afds.africadatasolution.modules.auth.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Rotating refresh tokens with reuse detection.
 *
 * Only the SHA-256 hash of the opaque token is stored; the raw token is
 * returned to the client exactly once. Every use rotates to a new token in
 * the same family and revokes the old one. Presenting an already-revoked
 * token is treated as theft and burns the entire family, forcing re-login
 * on the affected device. Mirrors backend/src/services/refreshToken.service.ts.
 */
public interface RefreshTokenService {

    Issued issue(UUID userId, String ip, String userAgent);

    Issued issue(UUID userId, String ip, String userAgent, String family);

    /** Returns empty on any failure — caller should treat as 401. */
    Optional<Rotated> rotate(String presented, String ip, String userAgent);

    void revoke(String presented);

    void revokeAllForUser(UUID userId);

    record Issued(String token, String family, Instant expiresAt) {
    }

    record Rotated(UUID userId, Issued refresh) {
    }
}
