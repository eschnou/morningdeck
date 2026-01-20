package be.transcode.morningdeck.server.provider.emailreceive;

import jakarta.mail.*;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.email", name = "provider", havingValue = "imap", matchIfMissing = true)
public class ImapEmailProvider {
    private final EmailConfig emailConfig;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelayString = "${mail.fetch.interval:60}")
    public void fetchEmails() {
        try {
            Store store = connect();
            Folder folder = store.getFolder(emailConfig.getFolder());
            folder.open(Folder.READ_WRITE);

            // Search for unread messages
            Message[] messages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

            for (Message message : messages) {
                try {
                    EmailMessage emailMessage = processMessage((MimeMessage) message);
                    publishEmailReceivedEvent(emailMessage);
                    message.setFlag(Flags.Flag.SEEN, true);
                } catch (Exception e) {
                    log.error("Error processing message: {}", e.getMessage(), e);
                }
            }

            folder.close(false);
            store.close();
        } catch (Exception e) {
            log.error("Error fetching emails: {}", e.getMessage(), e);
        }
    }

    private Store connect() throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", emailConfig.getProtocol());
        props.put("mail.pop3s.ssl.enable", emailConfig.isEnableSsl());
        props.put("mail.imaps.ssl.enable", emailConfig.isEnableSsl());
        props.put("mail." + emailConfig.getProtocol() + ".host", emailConfig.getHost());
        props.put("mail." + emailConfig.getProtocol() + ".port", emailConfig.getPort());

        Session session = Session.getInstance(props);
        Store store = session.getStore();
        store.connect(emailConfig.getHost(), emailConfig.getUsername(), emailConfig.getPassword());
        return store;
    }

    private EmailMessage processMessage(MimeMessage message) throws MessagingException, IOException {
        List<AttachmentInfo> attachments = new ArrayList<>();
        String content = "";

        if (message.getContent() instanceof Multipart multipart) {
            content = processMultipart(multipart, attachments);
        } else {
            content = message.getContent().toString();
        }

        return EmailMessage.builder()
                .messageId(message.getMessageID())
                .from(Arrays.toString(message.getFrom()))
                .to(Arrays.stream(message.getAllRecipients())
                        .map(Address::toString)
                        .collect(Collectors.toList()))
                .subject(message.getSubject())
                .content(content)
                .receivedDate(message.getReceivedDate().toInstant())
                .attachments(attachments)
                .build();
    }

    private String processMultipart(Multipart multipart, List<AttachmentInfo> attachments)
            throws MessagingException, IOException {
        StringBuilder contentBuilder = new StringBuilder();

        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);

            if (bodyPart.getDisposition() != null &&
                    bodyPart.getDisposition().equalsIgnoreCase(Part.ATTACHMENT)) {
                // Handle attachment
                MimeBodyPart mimeBodyPart = (MimeBodyPart) bodyPart;
                attachments.add(AttachmentInfo.builder()
                        .filename(mimeBodyPart.getFileName())
                        .contentType(mimeBodyPart.getContentType())
                        .content(mimeBodyPart.getInputStream().readAllBytes())
                        .build());
            } else {
                // Handle content
                if (bodyPart.getContent() instanceof Multipart nestedMultipart) {
                    contentBuilder.append(processMultipart(nestedMultipart, attachments));
                } else {
                    contentBuilder.append(bodyPart.getContent().toString());
                }
            }
        }

        return contentBuilder.toString();
    }

    private void publishEmailReceivedEvent(EmailMessage email) {
        log.debug("Publishing email received event for message: {}", email.getMessageId());
        eventPublisher.publishEvent(new EmailReceivedEvent(this, email));
    }
}
