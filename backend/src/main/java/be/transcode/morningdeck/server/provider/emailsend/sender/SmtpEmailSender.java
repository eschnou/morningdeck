package be.transcode.morningdeck.server.provider.emailsend.sender;

import be.transcode.morningdeck.server.provider.emailsend.exceptions.EmailSendException;
import be.transcode.morningdeck.server.provider.emailsend.model.Email;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.email", name = "sender", havingValue = "smtp", matchIfMissing = true)
public class SmtpEmailSender implements EmailSender {

    @Value("${smtp.host}")
    private String smtpHost;

    @Value("${smtp.port}")
    private int smtpPort;

    @Value("${smtp.username}")
    private String smtpUsername;

    @Value("${smtp.password}")
    private String smtpPassword;

    @Value("${smtp.from}")
    private String fromEmail;

    @Override
    public void send(Email email) throws EmailSendException {
        try {
            // Set up mail server properties
            Properties properties = new Properties();
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.host", smtpHost);
            properties.put("mail.smtp.port", smtpPort);

            // Create session with authentication
            Session session = Session.getInstance(properties, new jakarta.mail.Authenticator() {
                protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new jakarta.mail.PasswordAuthentication(smtpUsername, smtpPassword);
                }
            });

            // Create message
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email.getTo()));
            message.setSubject(email.getSubject());
            message.setText(email.getContent());

            // Send message
            Transport.send(message);
        } catch (MessagingException e) {
            throw new EmailSendException("Failed to send email: " + e.getMessage(), e);
        }
    }
}
