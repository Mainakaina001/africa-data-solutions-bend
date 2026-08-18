package afds.africadatasolution.common.security;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * RFC 6238 TOTP (HMAC-SHA1, 30s step, 6 digits) — the same parameters as
 * backend/src/services/twoFactor.service.ts's speakeasy configuration.
 */
@Component
public class Totp {

    private static final int SECRET_BYTES = 20; // speakeasy's default `length: 20`
    private static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;

    private final SecureRandom random = new SecureRandom();
    private final Base32 base32 = new Base32();

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32.encodeToString(bytes).replace("=", "");
    }

    public String buildOtpauthUrl(String issuer, String accountLabel, String secretBase32) {
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        String encodedLabel = URLEncoder.encode(issuer + ":" + accountLabel, StandardCharsets.UTF_8);
        return "otpauth://totp/" + encodedLabel
                + "?secret=" + secretBase32
                + "&issuer=" + encodedIssuer
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /** Verifies a 6-digit code within +/- {@code window} time steps of now. */
    public boolean verify(String secretBase32, String code, int window) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) return false;
        long currentStep = System.currentTimeMillis() / 1000 / STEP_SECONDS;
        byte[] key = base32.decode(secretBase32);
        for (long step = currentStep - window; step <= currentStep + window; step++) {
            if (code.equals(generateCode(key, step))) return true;
        }
        return false;
    }

    private String generateCode(byte[] key, long step) {
        try {
            byte[] data = new byte[8];
            for (int i = 7; i >= 0; i--) {
                data[i] = (byte) (step & 0xFF);
                step >>= 8;
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute TOTP", e);
        }
    }
}
