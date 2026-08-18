package afds.africadatasolution.modules.auth.service.impl;

import afds.africadatasolution.common.config.properties.JwtProperties;
import afds.africadatasolution.domain.token.RefreshToken;
import afds.africadatasolution.domain.token.RefreshTokenRepository;
import afds.africadatasolution.modules.auth.service.RefreshTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenServiceImpl(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    private Duration ttl() {
        return Duration.ofDays(jwtProperties.refreshTtlDays());
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    @Override
    public Issued issue(UUID userId, String ip, String userAgent) {
        return issue(userId, ip, userAgent, UUID.randomUUID().toString());
    }

    @Transactional
    @Override
    public Issued issue(UUID userId, String ip, String userAgent, String family) {
        String raw = randomToken();
        Instant expiresAt = Instant.now().plus(ttl());

        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(hash(raw));
        entity.setFamily(family);
        entity.setExpiresAt(expiresAt);
        entity.setIp(ip);
        entity.setUserAgent(userAgent);
        refreshTokenRepository.save(entity);

        return new Issued(raw, family, expiresAt);
    }

    /** Returns empty on any failure — caller should treat as 401. */
    @Transactional
    @Override
    public Optional<Rotated> rotate(String presented, String ip, String userAgent) {
        String tokenHash = hash(presented);
        Optional<RefreshToken> maybeRecord = refreshTokenRepository.findByTokenHash(tokenHash);
        if (maybeRecord.isEmpty()) return Optional.empty();
        RefreshToken record = maybeRecord.get();

        if (record.getRevokedAt() != null) {
            refreshTokenRepository.revokeFamily(record.getFamily(), Instant.now());
            return Optional.empty();
        }
        if (record.getExpiresAt().isBefore(Instant.now())) return Optional.empty();

        Issued newIssued = issue(record.getUserId(), ip, userAgent, record.getFamily());

        record.setRevokedAt(Instant.now());
        // replacedById is best-effort metadata; the new row's id isn't known without a second lookup,
        // so we look it up by the hash we just issued.
        refreshTokenRepository.findByTokenHash(hash(newIssued.token())).ifPresent(created ->
                record.setReplacedById(created.getId()));

        return Optional.of(new Rotated(record.getUserId(), newIssued));
    }

    @Transactional
    @Override
    public void revoke(String presented) {
        refreshTokenRepository.revokeByTokenHash(hash(presented), Instant.now());
    }

    @Transactional
    @Override
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }
}
