package afds.africadatasolution.common.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/** Cryptographically-secure OTP / opaque-token generation — mirrors backend/src/utils/otp.ts. */
@Component
public class OtpGenerator {

    private final SecureRandom random = new SecureRandom();

    public String generateNumeric() {
        int value = random.nextInt(1_000_000);
        return String.format("%06d", value);
    }

    /** 32-byte URL-safe random token, e.g. for email magic links. */
    public String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
