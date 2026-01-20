package be.transcode.morningdeck.server.provider.emailsend.sender;

import be.transcode.morningdeck.server.provider.emailsend.exceptions.EmailSendException;
import be.transcode.morningdeck.server.provider.emailsend.model.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "application.email", name = "sender", havingValue = "logs")
public class LogsEmailSender implements EmailSender {

    @Override
    public void send(Email email) throws EmailSendException {
        log.info("=== EMAIL (not sent) ===");
        log.info("To: {}", email.getTo());
        log.info("From: {}", email.getFrom());
        log.info("Subject: {}", email.getSubject());
        log.info("Content:\n{}", email.getContent());
        log.info("========================");
    }
}
