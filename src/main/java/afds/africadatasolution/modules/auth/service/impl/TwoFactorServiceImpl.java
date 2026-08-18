package afds.africadatasolution.modules.auth.service.impl;

import afds.africadatasolution.common.exception.AuthenticationException;
import afds.africadatasolution.common.exception.NotFoundException;
import afds.africadatasolution.common.exception.ValidationException;
import afds.africadatasolution.common.config.properties.AppProperties;
import afds.africadatasolution.domain.user.User;
import afds.africadatasolution.domain.user.UserRepository;
import afds.africadatasolution.common.security.Totp;
import afds.africadatasolution.modules.auth.service.TwoFactorService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class TwoFactorServiceImpl implements TwoFactorService {

    private static final int BACKUP_CODE_COUNT = 8;
    private static final int VERIFY_WINDOW = 1;

    private final UserRepository userRepository;
    private final Totp totp;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final SecureRandom random = new SecureRandom();

    public TwoFactorServiceImpl(UserRepository userRepository, Totp totp, PasswordEncoder passwordEncoder,
                                 AppProperties appProperties) {
        this.userRepository = userRepository;
        this.totp = totp;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
    }

    @Transactional
    @Override
    public SetupResult setup(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if (user.isTwoFactorEnabled()) {
            throw new ValidationException("2FA is already enabled");
        }

        String secret = totp.generateSecret();
        user.setTwoFactorSecret(secret);

        String otpauthUrl = totp.buildOtpauthUrl(appProperties.name(), user.getEmail(), secret);
        String qrCodeDataUrl = toQrDataUrl(otpauthUrl);
        return new SetupResult(otpauthUrl, qrCodeDataUrl);
    }

    @Transactional
    @Override
    public List<String> verifyAndEnable(UUID userId, String code) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getTwoFactorSecret() == null) {
            throw new ValidationException("2FA setup not started");
        }
        if (user.isTwoFactorEnabled()) {
            throw new ValidationException("2FA is already enabled");
        }
        if (!totp.verify(user.getTwoFactorSecret(), code, VERIFY_WINDOW)) {
            throw new AuthenticationException("Invalid 2FA code");
        }

        List<String> rawCodes = new ArrayList<>();
        List<String> hashedCodes = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String raw = randomBackupCode();
            rawCodes.add(raw);
            hashedCodes.add(passwordEncoder.encode(raw));
        }

        user.setTwoFactorEnabled(true);
        user.setTwoFactorBackupCodes(hashedCodes);
        return rawCodes;
    }

    @Transactional
    @Override
    public boolean verify(UUID userId, String code) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if (!user.isTwoFactorEnabled() || user.getTwoFactorSecret() == null) return false;

        if (totp.verify(user.getTwoFactorSecret(), code, VERIFY_WINDOW)) return true;

        List<String> backupCodes = user.getTwoFactorBackupCodes();
        for (int i = 0; i < backupCodes.size(); i++) {
            if (passwordEncoder.matches(code, backupCodes.get(i))) {
                List<String> remaining = new ArrayList<>(backupCodes);
                remaining.remove(i);
                user.setTwoFactorBackupCodes(remaining);
                return true;
            }
        }
        return false;
    }

    @Transactional
    @Override
    public void disable(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        user.setTwoFactorBackupCodes(new ArrayList<>());
    }

    private String randomBackupCode() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private String toQrDataUrl(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 256, 256);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Failed to generate 2FA QR code", e);
        }
    }
}
