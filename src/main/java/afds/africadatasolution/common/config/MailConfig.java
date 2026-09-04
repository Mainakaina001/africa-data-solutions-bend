package afds.africadatasolution.common.config;

import afds.africadatasolution.common.config.properties.EmailProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Gmail SMTP mail sender. Credentials are optional — {@code EmailServiceImpl}
 * checks them before sending and no-ops (with a log warning) if absent, so a
 * missing app password never breaks startup.
 */
@Configuration
public class MailConfig {

    @Bean
    public JavaMailSenderImpl javaMailSender(EmailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.gmail.com");
        sender.setPort(587);
        sender.setUsername(properties.gmailUsername());
        sender.setPassword(properties.gmailAppPassword());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "20000");
        props.put("mail.smtp.writetimeout", "20000");
        return sender;
    }
}
