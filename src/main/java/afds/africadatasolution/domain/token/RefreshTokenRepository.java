package afds.africadatasolution.domain.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.family = :family and r.revokedAt is null")
    void revokeFamily(@Param("family") String family, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.tokenHash = :tokenHash and r.revokedAt is null")
    void revokeByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.userId = :userId and r.revokedAt is null")
    void revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
