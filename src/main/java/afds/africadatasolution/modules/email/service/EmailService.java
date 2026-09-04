package afds.africadatasolution.modules.email.service;

/**
 * Transactional email sent via Gmail SMTP.
 * Never throws — a failed email must not break the calling request.
 */
public interface EmailService {

    void send(String to, String subject, String html);

    void sendPasswordResetOtp(String to, String firstName, String otp);

    void sendPasswordChangedConfirmation(String to, String firstName);

    void sendWelcome(String to, String firstName);

    void sendPinChangedConfirmation(String to, String firstName);
}
