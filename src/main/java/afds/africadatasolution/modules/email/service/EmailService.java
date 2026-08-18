package afds.africadatasolution.modules.email.service;

/**
 * Transactional email via Resend's HTTP API (works on any hosting platform,
 * no SMTP port dependency). Mirrors backend/src/services/email.service.ts.
 * Never throws — a failed email must not break the calling request.
 */
public interface EmailService {

    void send(String to, String subject, String html);

    void sendPasswordResetOtp(String to, String firstName, String otp);

    void sendPasswordChangedConfirmation(String to, String firstName);

    void sendWelcome(String to, String firstName);

    void sendPinChangedConfirmation(String to, String firstName);
}
