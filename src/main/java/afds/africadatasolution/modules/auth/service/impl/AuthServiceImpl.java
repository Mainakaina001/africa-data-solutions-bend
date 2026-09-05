package afds.africadatasolution.modules.auth.service.impl;

import afds.africadatasolution.common.config.properties.WalletProperties;
import afds.africadatasolution.common.exception.AuthenticationException;
import afds.africadatasolution.common.exception.ConflictException;
import afds.africadatasolution.common.exception.NotFoundException;
import afds.africadatasolution.common.exception.ValidationException;
import afds.africadatasolution.common.security.JwtService;
import afds.africadatasolution.common.security.OtpGenerator;
import afds.africadatasolution.common.security.PasswordPolicy;
import afds.africadatasolution.domain.user.User;
import afds.africadatasolution.domain.user.UserRepository;
import afds.africadatasolution.domain.virtualaccount.VirtualAccount;
import afds.africadatasolution.domain.virtualaccount.VirtualAccountRepository;
import afds.africadatasolution.domain.wallet.Wallet;
import afds.africadatasolution.domain.wallet.WalletRepository;
import afds.africadatasolution.modules.audit.service.AuditService;
import afds.africadatasolution.modules.auth.service.RefreshTokenService;
import afds.africadatasolution.modules.auth.service.TwoFactorService;
import afds.africadatasolution.modules.auth.dto.request.*;
import afds.africadatasolution.modules.auth.dto.response.*;
import afds.africadatasolution.modules.auth.service.AuthService;
import afds.africadatasolution.modules.email.service.EmailService;
import afds.africadatasolution.modules.external.paymentpoint.PaymentPointClient;
import afds.africadatasolution.modules.external.paymentpoint.PaymentPointVirtualAccountResponse;
import afds.africadatasolution.modules.payment.dto.response.VirtualAccountSummary;
import afds.africadatasolution.modules.wallet.dto.response.WalletSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Authentication, session, and account-security flows.
 * Mirrors backend/src/controllers/auth.controller.ts.
 */
@Service
public class AuthServiceImpl implements AuthService{
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final long RESET_TOKEN_TTL_MINUTES = 15;
    private static final int RESET_MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final RefreshTokenService refreshTokenService;
    private final TwoFactorService twoFactorService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final OtpGenerator otpGenerator;
    private final EmailService emailService;
    private final AuditService auditService;
    private final PaymentPointClient paymentPointClient;
    private final WalletProperties walletProperties;

    public AuthServiceImpl(UserRepository userRepository, WalletRepository walletRepository,
                       VirtualAccountRepository virtualAccountRepository,
                       RefreshTokenService refreshTokenService, TwoFactorService twoFactorService, JwtService jwtService,
                       PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy, OtpGenerator otpGenerator,
                       EmailService emailService, AuditService auditService, PaymentPointClient paymentPointClient,
                       WalletProperties walletProperties) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.virtualAccountRepository = virtualAccountRepository;
        this.refreshTokenService = refreshTokenService;
        this.twoFactorService = twoFactorService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.otpGenerator = otpGenerator;
        this.emailService = emailService;
        this.auditService = auditService;
        this.paymentPointClient = paymentPointClient;
        this.walletProperties = walletProperties;
    }

    private AuthTokens issueSession(User user, String ip, String userAgent) {
        String accessToken = jwtService.sign(user.getId(), user.getRole(), user.getTokenVersion());
        RefreshTokenService.Issued refresh = refreshTokenService.issue(user.getId(), ip, userAgent);
        return new AuthTokens(accessToken, refresh.token(), refresh.expiresAt());
    }

    @Transactional
    @Override
    public RegisterResponse register(RegisterRequest request, String ip, String userAgent) {
        PasswordPolicy.StrengthResult strength = passwordPolicy.validateSignupPassword(request.password());
        if (!strength.valid()) {
            throw new ValidationException("Password validation failed: " + String.join(", ", strength.errors()));
        }

        if (userRepository.existsByEmailOrPhone(request.email(), request.phone())) {
            throw new ConflictException(
                    "Unable to register with the provided details. If you already have an account, please log in.");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        Wallet wallet = new Wallet();
        wallet.setUserId(user.getId());
        walletRepository.save(wallet);

        VirtualAccountSummary virtualAccount = tryCreateVirtualAccount(user);

        AuthTokens tokens = issueSession(user, ip, userAgent);

        emailService.sendWelcome(user.getEmail(), user.getFirstName());
        // auditService.log runs REQUIRES_NEW (its own independent transaction) so the audit trail
        // survives a rollback of this one — but that means it can't see this method's still-uncommitted
        // INSERT of `user` yet, and the audit_logs FK on user_id would fail. Defer it until this
        // transaction actually commits, so the referenced row is guaranteed to exist.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    auditService.log(user.getId(), "register", ip, userAgent, null, true);
                }
            });
        } else {
            auditService.log(user.getId(), "register", ip, userAgent, null, true);
        }

        return new RegisterResponse(UserSummary.from(user), tokens.accessToken(), tokens.refreshToken(),
                tokens.refreshExpiresAt(), virtualAccount);
    }

    private VirtualAccountSummary tryCreateVirtualAccount(User user) {
        try {
            String reference = "VA_" + user.getId().toString().substring(0, 8) + "_" + System.currentTimeMillis();
            String fullName = (user.getFirstName() + " " + user.getLastName()).trim();
            PaymentPointVirtualAccountResponse response =
                    paymentPointClient.generateVirtualAccount(user.getEmail(), fullName, user.getPhone(), PaymentPointClient.DEFAULT_BANK);

            var accountDetails = response.bankAccounts().isEmpty() ? null : response.bankAccounts().get(0);
            if (accountDetails == null) return null;

            VirtualAccount va = new VirtualAccount();
            va.setUserId(user.getId());
            va.setAccountReference(reference);
            va.setAccountNumber(accountDetails.accountNumber());
            va.setAccountName(accountDetails.accountName());
            va.setBankName(accountDetails.bankName());
            va.setBankCode(accountDetails.bankCode());
            va.setProvider("paymentpoint");
            va.setMetadata(java.util.Map.of("createdVia", "auto_registration"));
            virtualAccountRepository.save(va);
            return VirtualAccountSummary.from(va);
        } catch (Exception e) {
            log.error("Virtual account creation failed during registration userId={} error={}", user.getId(), e.getMessage());
            return null;
        }
    }

    @Transactional
    @Override
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        Optional<User> maybeUser = userRepository.findByEmail(request.email());
        if (maybeUser.isEmpty()) {
            // Burn equivalent time hashing a decoy so the user-missing branch matches user-found latency.
            passwordEncoder.encode("decoy-decoy-decoy-decoy-1!");
            auditService.log(null, "login_failed_no_user", ip, userAgent, java.util.Map.of("email", request.email()), false);
            throw new AuthenticationException("Invalid email or password");
        }
        User user = maybeUser.get();

        if (!user.isActive()) throw new AuthenticationException("Account is deactivated");
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new AuthenticationException("Account temporarily locked. Try again later.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= walletProperties.loginMaxFailedAttempts()) {
                user.setLockedUntil(Instant.now().plus(walletProperties.loginLockoutMinutes(), ChronoUnit.MINUTES));
            }
            auditService.log(user.getId(), "login_failed_bad_password", ip, userAgent, java.util.Map.of("attempts", attempts), false);
            throw new AuthenticationException("Invalid email or password");
        }

        if (user.isTwoFactorEnabled()) {
            if (request.twoFactorCode() == null || request.twoFactorCode().isBlank()) {
                return LoginResponse.requiresTwoFactor();
            }
            if (!twoFactorService.verify(user.getId(), request.twoFactorCode())) {
                auditService.log(user.getId(), "login_failed_2fa", ip, userAgent, null, false);
                throw new AuthenticationException("Invalid 2FA code");
            }
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        user.setLastLoginIp(ip);

        AuthTokens tokens = issueSession(user, ip, userAgent);
        auditService.log(user.getId(), "login_success", ip, userAgent, null, true);

        return LoginResponse.authenticated(AuthenticatedUserView.from(user), user.isTwoFactorEnabled(), tokens);
    }

    @Transactional
    @Override
    public AuthTokens refresh(RefreshRequest request, String ip, String userAgent) {
        var rotated = refreshTokenService.rotate(request.refreshToken(), ip, userAgent)
                .orElseThrow(() -> new AuthenticationException("Invalid or expired refresh token"));

        User user = userRepository.findById(rotated.userId())
                .filter(User::isActive)
                .orElseThrow(() -> new AuthenticationException("Session no longer valid"));

        String accessToken = jwtService.sign(user.getId(), user.getRole(), user.getTokenVersion());
        return new AuthTokens(accessToken, rotated.refresh().token(), rotated.refresh().expiresAt());
    }

    @Transactional
    @Override
    public void logout(LogoutRequest request) {
        if (request.refreshToken() != null && !request.refreshToken().isBlank()) {
            refreshTokenService.revoke(request.refreshToken());
        }
    }

    @Transactional
    @Override
    public void logoutEverywhere(UUID userId, String ip, String userAgent) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        user.setTokenVersion(user.getTokenVersion() + 1);
        refreshTokenService.revokeAllForUser(userId);
        auditService.log(userId, "logout_everywhere", ip, userAgent, null, true);
    }

    @Transactional(readOnly = true)
    @Override
    public ProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        var wallet = walletRepository.findByUserId(userId).map(WalletSummary::from).orElse(null);
        var virtualAccount = virtualAccountRepository.findFirstByUserIdAndIsActiveTrue(userId)
                .map(VirtualAccountSummary::from).orElse(null);

        return new ProfileResponse(user.getId(), user.getEmail(), user.getPhone(), user.getFirstName(), user.getLastName(),
                user.getRole(), user.isActive(), user.isVerified(), user.isTwoFactorEnabled(), user.getCreatedAt(),
                wallet, virtualAccount);
    }

    @Transactional
    @Override
    public void forgotPassword(ForgotPasswordRequest request, String ip) {
        Optional<User> maybeUser = userRepository.findByEmail(request.email());
        if (maybeUser.isPresent()) {
            User user = maybeUser.get();
            String code = otpGenerator.generateNumeric();
            user.setResetTokenHash(passwordEncoder.encode(code));
            user.setResetTokenExpiry(Instant.now().plus(RESET_TOKEN_TTL_MINUTES, ChronoUnit.MINUTES));
            user.setResetTokenAttempts(0);

            emailService.sendPasswordResetOtp(user.getEmail(), user.getFirstName(), code);
            auditService.log(user.getId(), "password_reset_requested", ip, null, null, true);
        } else {
            passwordEncoder.encode("000000"); // burn equivalent time
        }
        // Always respond identically regardless of whether the email is registered — caller returns a generic message.
    }

    @Transactional
    @Override
    public void resetPassword(ResetPasswordRequest request, String ip) {
        PasswordPolicy.StrengthResult strength = passwordPolicy.validatePasswordStrength(request.newPassword());
        if (!strength.valid()) {
            throw new ValidationException("Password validation failed: " + String.join(", ", strength.errors()));
        }

        User user = userRepository.findByEmail(request.email())
                .filter(u -> u.getResetTokenHash() != null && u.getResetTokenExpiry() != null
                        && u.getResetTokenExpiry().isAfter(Instant.now()))
                .orElseThrow(() -> new ValidationException("Invalid or expired reset token"));

        if (user.getResetTokenAttempts() >= RESET_MAX_ATTEMPTS) {
            user.setResetTokenHash(null);
            user.setResetTokenExpiry(null);
            user.setResetTokenAttempts(0);
            throw new ValidationException("Too many incorrect attempts. Please request a new code.");
        }

        if (!passwordEncoder.matches(request.resetToken(), user.getResetTokenHash())) {
            user.setResetTokenAttempts(user.getResetTokenAttempts() + 1);
            throw new ValidationException("Invalid or expired reset token");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setResetTokenHash(null);
        user.setResetTokenExpiry(null);
        user.setResetTokenAttempts(0);
        user.setTokenVersion(user.getTokenVersion() + 1);
        refreshTokenService.revokeAllForUser(user.getId());

        emailService.sendPasswordChangedConfirmation(user.getEmail(), user.getFirstName());
        auditService.log(user.getId(), "password_reset_completed", ip, null, null, true);
    }

    @Transactional
    @Override
    public void changePassword(UUID userId, ChangePasswordRequest request, String ip) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AuthenticationException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new AuthenticationException("Current password is incorrect");
        }

        PasswordPolicy.StrengthResult strength = passwordPolicy.validatePasswordStrength(request.newPassword());
        if (!strength.valid()) {
            throw new ValidationException("Password validation failed: " + String.join(", ", strength.errors()));
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new ValidationException("New password must be different");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        refreshTokenService.revokeAllForUser(userId);

        emailService.sendPasswordChangedConfirmation(user.getEmail(), user.getFirstName());
        auditService.log(userId, "password_changed", ip, null, null, true);
    }

    @Transactional
    @Override
    public void createTransactionPin(UUID userId, CreatePinRequest request, String ip) {
        PasswordPolicy.PinResult pinResult = passwordPolicy.validatePinStrength(request.pin());
        if (!pinResult.valid()) throw new ValidationException(pinResult.error());

        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getTransactionPin() != null) {
            throw new ConflictException("Transaction PIN already exists. Use change-pin to update it.");
        }

        user.setTransactionPin(passwordEncoder.encode(request.pin()));
        user.setFailedPinAttempts(0);
        user.setPinLockedUntil(null);
        auditService.log(userId, "pin_created", ip, null, null, true);
    }

    @Transactional
    @Override
    public void changeTransactionPin(UUID userId, ChangePinRequest request, String ip) {
        PasswordPolicy.PinResult pinResult = passwordPolicy.validatePinStrength(request.newPin());
        if (!pinResult.valid()) throw new ValidationException(pinResult.error());

        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getTransactionPin() == null) {
            throw new ValidationException("No transaction PIN exists. Use create-pin first.");
        }
        if (!passwordEncoder.matches(request.currentPin(), user.getTransactionPin())) {
            throw new AuthenticationException("Current PIN is incorrect");
        }
        if (passwordEncoder.matches(request.newPin(), user.getTransactionPin())) {
            throw new ValidationException("New PIN must be different from current PIN");
        }

        user.setTransactionPin(passwordEncoder.encode(request.newPin()));
        user.setFailedPinAttempts(0);
        user.setPinLockedUntil(null);
        user.setTokenVersion(user.getTokenVersion() + 1);
        refreshTokenService.revokeAllForUser(userId);

        emailService.sendPinChangedConfirmation(user.getEmail(), user.getFirstName());
        auditService.log(userId, "pin_changed", ip, null, null, true);
    }

    @Override
    public TwoFactorSetupResponse setup2FA(UUID userId) {
        var result = twoFactorService.setup(userId);
        return new TwoFactorSetupResponse(result.otpauthUrl(), result.qrCodeDataUrl());
    }

    @Transactional
    @Override
    public TwoFactorEnableResponse enable2FA(UUID userId, Enable2FARequest request, String ip) {
        List<String> backupCodes = twoFactorService.verifyAndEnable(userId, request.code());
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        user.setTokenVersion(user.getTokenVersion() + 1);
        auditService.log(userId, "2fa_enabled", ip, null, null, true);
        return new TwoFactorEnableResponse(backupCodes);
    }

    @Transactional
    @Override
    public void disable2FA(UUID userId, Disable2FARequest request, String ip) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AuthenticationException("Invalid password");
        }
        twoFactorService.disable(userId);
        user.setTokenVersion(user.getTokenVersion() + 1);
        auditService.log(userId, "2fa_disabled", ip, null, null, true);
    }
}
