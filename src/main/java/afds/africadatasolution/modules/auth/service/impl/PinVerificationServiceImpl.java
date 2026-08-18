package afds.africadatasolution.modules.auth.service.impl;

import afds.africadatasolution.common.exception.AuthenticationException;
import afds.africadatasolution.common.exception.ValidationException;
import afds.africadatasolution.common.config.properties.WalletProperties;
import afds.africadatasolution.domain.user.User;
import afds.africadatasolution.domain.user.UserRepository;
import afds.africadatasolution.modules.auth.service.PinVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class PinVerificationServiceImpl implements PinVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PinVerificationServiceImpl.class);
    private static final int PIN_LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletProperties walletProperties;

    public PinVerificationServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, WalletProperties walletProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.walletProperties = walletProperties;
    }

    @Transactional
    @Override
    public void verify(UUID userId, String pin) {
        if (pin == null || !pin.matches("^\\d{4,6}$")) {
            throw new ValidationException("Transaction PIN is required");
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new AuthenticationException("User not found"));
        if (user.getTransactionPin() == null) {
            throw new ValidationException("Set a transaction PIN before continuing");
        }
        if (user.getPinLockedUntil() != null && user.getPinLockedUntil().isAfter(Instant.now())) {
            throw new AuthenticationException("PIN temporarily locked. Try again later or reset your PIN.");
        }

        if (!passwordEncoder.matches(pin, user.getTransactionPin())) {
            int attempts = user.getFailedPinAttempts() + 1;
            user.setFailedPinAttempts(attempts);
            if (attempts >= walletProperties.pinMaxFailedAttempts()) {
                user.setPinLockedUntil(Instant.now().plus(PIN_LOCK_MINUTES, ChronoUnit.MINUTES));
            }
            log.warn("Failed PIN attempt userId={} attempts={}", userId, attempts);
            throw new AuthenticationException("Incorrect PIN");
        }

        if (user.getFailedPinAttempts() != 0 || user.getPinLockedUntil() != null) {
            user.setFailedPinAttempts(0);
            user.setPinLockedUntil(null);
        }
    }
}
