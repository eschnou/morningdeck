package be.transcode.morningdeck.server.provider.emailsend.sender;

import be.transcode.morningdeck.server.provider.emailsend.exceptions.EmailSendException;
import be.transcode.morningdeck.server.provider.emailsend.model.Email;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "application.email", name = "sender", havingValue = "aws", matchIfMissing = false)
public class AWSEmailSender implements EmailSender {

    private final SesClient sesClient;

    public AWSEmailSender(SesClient sesClient) {
        this.sesClient = sesClient;
    }

    @Override
    public void send(Email email) throws EmailSendException {
        try {
            SendEmailRequest request = SendEmailRequest.builder()
                    .destination(Destination.builder()
                            .toAddresses(email.getTo())
                            .build())
                    .message(Message.builder()
                            .body(Body.builder()
                                    .html(Content.builder()
                                            .charset("UTF-8")
                                            .data(email.getContent())
                                            .build())
                                    .text(Content.builder()
                                            .charset("UTF-8")
                                            .data(stripHtml(email.getContent()))
                                            .build())
                                    .build())
                            .subject(Content.builder()
                                    .charset("UTF-8")
                                    .data(email.getSubject())
                                    .build())
                            .build())
                    .source(email.getFrom())
                    .build();

            sesClient.sendEmail(request);
            log.info("Email sent successfully to {}", email.getTo());
        } catch (SesException e) {
            log.error("Failed to send email to {}", email.getTo());
            throw new EmailSendException("Failed to send email", e);
        }
    }

    private String stripHtml(String html) {
        return Jsoup.parse(html).text();
    }
}
