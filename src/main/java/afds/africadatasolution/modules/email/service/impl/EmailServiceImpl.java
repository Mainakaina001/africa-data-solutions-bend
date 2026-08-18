package afds.africadatasolution.modules.email.service.impl;

import afds.africadatasolution.common.config.properties.AppProperties;
import afds.africadatasolution.common.config.properties.EmailProperties;
import afds.africadatasolution.modules.email.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Year;
import java.util.Map;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final RestTemplate resendRestTemplate;
    private final EmailProperties properties;
    private final String appName;

    public EmailServiceImpl(RestTemplate resendRestTemplate, EmailProperties properties,
                             AppProperties appProperties) {
        this.resendRestTemplate = resendRestTemplate;
        this.properties = properties;
        this.appName = appProperties.name();
    }

    @Async
    @Override
    public void send(String to, String subject, String html) {
        if (properties.resendApiKey() == null || properties.resendApiKey().isBlank()) {
            log.warn("Email not configured — set RESEND_API_KEY to enable emails to={} subject={}", to, subject);
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(properties.resendApiKey());
            Map<String, Object> body = Map.of(
                    "from", appName + " <" + properties.from() + ">",
                    "to", to,
                    "subject", subject,
                    "html", html);
            resendRestTemplate.postForEntity("/emails", new HttpEntity<>(body, headers), Void.class);
            log.info("Email sent successfully to={} subject={}", to, subject);
        } catch (RestClientException e) {
            log.error("Failed to send email to={} subject={} error={}", to, subject, e.getMessage());
        }
    }

    private String layout(String content) {
        return """
                <!DOCTYPE html>
                <html>
                  <head><meta charset="utf-8" /><meta name="viewport" content="width=device-width,initial-scale=1.0" /></head>
                  <body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,sans-serif;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:40px 0;">
                      <tr><td align="center">
                        <table width="600" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;max-width:600px;">
                          <tr>
                            <td style="background:#1a56db;padding:32px;text-align:center;">
                              <h1 style="margin:0;color:#ffffff;font-size:24px;">%s</h1>
                            </td>
                          </tr>
                          <tr><td style="padding:40px 32px;">%s</td></tr>
                          <tr>
                            <td style="background:#f9fafb;padding:24px 32px;text-align:center;border-top:1px solid #e5e7eb;">
                              <p style="margin:0;font-size:12px;color:#9ca3af;">&copy; %d %s. All rights reserved.</p>
                            </td>
                          </tr>
                        </table>
                      </td></tr>
                    </table>
                  </body>
                </html>
                """.formatted(appName, content, Year.now().getValue(), appName);
    }

    @Override
    public void sendPasswordResetOtp(String to, String firstName, String otp) {
        send(to, "Your Password Reset Code — " + appName, layout("""
                <p style="margin:0 0 16px;font-size:16px;color:#374151;">Hi <strong>%s</strong>,</p>
                <p style="margin:0 0 24px;font-size:16px;color:#374151;line-height:1.5;">
                  We received a request to reset your password. Use the code below — it expires in <strong>15 minutes</strong>.
                </p>
                <div style="text-align:center;margin:32px 0;">
                  <div style="display:inline-block;background:#f0f4ff;border:2px dashed #1a56db;border-radius:8px;padding:20px 40px;">
                    <p style="margin:0;font-size:12px;color:#6b7280;text-transform:uppercase;letter-spacing:2px;">Your Reset Code</p>
                    <p style="margin:8px 0 0;font-size:40px;font-weight:bold;color:#1a56db;letter-spacing:8px;">%s</p>
                  </div>
                </div>
                <p style="margin:0 0 8px;font-size:14px;color:#6b7280;line-height:1.5;">
                  If you didn't request this, ignore this email — your password will not change.
                </p>
                <p style="margin:0;font-size:14px;color:#6b7280;"><strong>Never share this code with anyone.</strong></p>
                """.formatted(firstName, otp)));
    }

    @Override
    public void sendPasswordChangedConfirmation(String to, String firstName) {
        send(to, "Password Changed — " + appName, layout("""
                <p style="margin:0 0 16px;font-size:16px;color:#374151;">Hi <strong>%s</strong>,</p>
                <div style="background:#f0fdf4;border-left:4px solid #22c55e;padding:16px;border-radius:4px;margin:0 0 24px;">
                  <p style="margin:0;font-size:16px;color:#166534;font-weight:600;">✓ Your password has been changed successfully.</p>
                </div>
                <p style="margin:0;font-size:14px;color:#6b7280;line-height:1.5;">
                  If you did not make this change, please reset your password immediately and contact our support team.
                </p>
                """.formatted(firstName)));
    }

    @Override
    public void sendWelcome(String to, String firstName) {
        send(to, "Welcome to " + appName + "!", layout("""
                <p style="margin:0 0 16px;font-size:16px;color:#374151;">Hi <strong>%s</strong>,</p>
                <p style="margin:0 0 24px;font-size:16px;color:#374151;line-height:1.5;">
                  Welcome to <strong>%s</strong>! Your account has been created successfully.
                </p>
                <p style="margin:0 0 8px;font-size:14px;color:#374151;">Here's what you can do:</p>
                <ul style="margin:0 0 24px;padding-left:20px;font-size:14px;color:#374151;line-height:2;">
                  <li>Fund your wallet using your dedicated virtual account</li>
                  <li>Purchase data bundles for any Nigerian network</li>
                  <li>Buy airtime instantly</li>
                  <li>Track all transactions in real time</li>
                </ul>
                <p style="margin:0;font-size:14px;color:#6b7280;">If you have any questions, please reach out to our support team.</p>
                """.formatted(firstName, appName)));
    }

    @Override
    public void sendPinChangedConfirmation(String to, String firstName) {
        send(to, "Transaction PIN Changed — " + appName, layout("""
                <p style="margin:0 0 16px;font-size:16px;color:#374151;">Hi <strong>%s</strong>,</p>
                <div style="background:#fffbeb;border-left:4px solid #f59e0b;padding:16px;border-radius:4px;margin:0 0 24px;">
                  <p style="margin:0;font-size:16px;color:#92400e;font-weight:600;">⚠ Your transaction PIN has been changed.</p>
                </div>
                <p style="margin:0;font-size:14px;color:#6b7280;line-height:1.5;">
                  If you did not make this change, please contact our support team immediately.
                </p>
                """.formatted(firstName)));
    }
}
