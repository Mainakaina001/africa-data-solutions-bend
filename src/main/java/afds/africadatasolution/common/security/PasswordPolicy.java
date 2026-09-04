package afds.africadatasolution.common.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Password/PIN strength rules — mirrors backend/src/utils/password.ts. */
@Component
public class PasswordPolicy {

    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*(),.?\":{}|<>_\\-+=/\\\\\\[\\]~`';]");
    private static final Pattern REPEATED = Pattern.compile("(.)\\1{3,}");
    private static final Pattern COMMON = Pattern.compile("^(?i)(?:password|qwerty|letmein|admin|welcome|p@ssword)");
    private static final Pattern ALPHANUMERIC = Pattern.compile("^[a-zA-Z0-9]{6,128}$");

    public StrengthResult validatePasswordStrength(String password) {
        List<String> errors = new ArrayList<>();
        if (password.length() < 12) errors.add("Password must be at least 12 characters long");
        if (password.length() > 128) errors.add("Password must be at most 128 characters long");
        if (!UPPER.matcher(password).find()) errors.add("Password must contain at least one uppercase letter");
        if (!LOWER.matcher(password).find()) errors.add("Password must contain at least one lowercase letter");
        if (!DIGIT.matcher(password).find()) errors.add("Password must contain at least one number");
        if (!SPECIAL.matcher(password).find()) errors.add("Password must contain at least one special character");
        if (REPEATED.matcher(password).find()) errors.add("Password must not contain 4 or more repeated characters");
        if (COMMON.matcher(password).find()) errors.add("Password is too common");
        return new StrengthResult(errors.isEmpty(), errors);
    }

    /**
     * Signup password rule: any alphanumeric string (letters and/or digits) of
     * 6-128 characters, still rejecting trivially guessable ones (4+ repeated
     * characters, common words like "password"/"qwerty").
     */
    public StrengthResult validateSignupPassword(String password) {
        List<String> errors = new ArrayList<>();
        if (password == null || !ALPHANUMERIC.matcher(password).matches()) {
            errors.add("Password must be 6-128 characters and contain only letters and numbers");
            return new StrengthResult(false, errors);
        }
        if (REPEATED.matcher(password).find()) errors.add("Password must not contain 4 or more repeated characters");
        if (COMMON.matcher(password).find()) errors.add("Password is too common");
        return new StrengthResult(errors.isEmpty(), errors);
    }

    public PinResult validatePinStrength(String pin) {
        if (!pin.matches("^\\d{6}$")) {
            return new PinResult(false, "PIN must be exactly 6 digits");
        }
        if (pin.matches("^(\\d)\\1{5}$")) {
            return new PinResult(false, "PIN cannot be all the same digit");
        }
        boolean ascending = List.of("012345", "123456", "234567", "345678", "456789", "987654", "876543").contains(pin);
        if (ascending) {
            return new PinResult(false, "PIN cannot be a simple sequence");
        }
        return new PinResult(true, null);
    }

    public record StrengthResult(boolean valid, List<String> errors) {
    }

    public record PinResult(boolean valid, String error) {
    }
}
