package be.transcode.morningdeck.server.provider.emailreceive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "application.email", name = "provider", havingValue = "sqs", matchIfMissing = false)
public class SQSEmailProvider {

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final S3Client s3Client;
    private final String bucketName;

    public SQSEmailProvider(
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            S3Client s3Client,
            @Value("${application.email.s3.bucket-name}") String bucketName) {
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @SqsListener("${application.email.sqs.queue-name}")
    public void handleSESMessage(String message) {
        try {
            // Parse the SNS wrapper message
            JsonNode snsWrapper = objectMapper.readTree(message);

            // Get the SES message from the SNS Message field
            String sesMessage = snsWrapper.get("Message").asText();
            JsonNode sesContent = objectMapper.readTree(sesMessage);

            // Get S3 information from the receipt action
            JsonNode action = sesContent.path("receipt")
                    .path("action");
            String s3Key = action.path("objectKey").asText();

            log.info("Retrieving email from S3 with key: {}", s3Key);

            // Get and parse the email from S3
            EmailMessage emailMessage = getEmailFromS3(s3Key);

            // Publish the event
            publishEmailReceivedEvent(emailMessage);

        } catch (Exception e) {
            log.error("Error processing SNS/SES message", e);
            throw new RuntimeException("Failed to process message", e);
        }
    }

    private EmailMessage getEmailFromS3(String objectKey) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getObjectRequest);

            // Create empty Session for parsing the email
            Session session = Session.getDefaultInstance(new Properties());

            // Parse the email using Jakarta Mail
            MimeMessage mimeMessage = new MimeMessage(session, response);

            EmailMessage emailMessage = new EmailMessage();
            emailMessage.setMessageId(mimeMessage.getMessageID());
            emailMessage.setSubject(mimeMessage.getSubject());

            // Get From address
            InternetAddress[] fromAddresses = (InternetAddress[]) mimeMessage.getFrom();
            if (fromAddresses != null && fromAddresses.length > 0) {
                emailMessage.setFrom(fromAddresses[0].getAddress());
            }

            // Get To addresses
            List<String> toAddresses = new ArrayList<>();
            InternetAddress[] recipients = (InternetAddress[]) mimeMessage.getAllRecipients();
            if (recipients != null) {
                for (InternetAddress recipient : recipients) {
                    toAddresses.add(recipient.getAddress());
                }
            }
            emailMessage.setTo(toAddresses);

            // Get content (this is simplified - you might want to handle multipart messages)
            Object content = mimeMessage.getContent();
            if (content instanceof String) {
                emailMessage.setContent((String) content);
            } else if (content instanceof jakarta.mail.Multipart) {
                // Handle multipart messages if needed
                jakarta.mail.Multipart multipart = (jakarta.mail.Multipart) content;
                // For now, just get the first text part
                for (int i = 0; i < multipart.getCount(); i++) {
                    jakarta.mail.BodyPart bodyPart = multipart.getBodyPart(i);
                    if (bodyPart.getContentType().toLowerCase().startsWith("text/plain")) {
                        emailMessage.setContent((String) bodyPart.getContent());
                        break;
                    }
                }
            }

            // Set received date from the email
            Date receivedDate = mimeMessage.getReceivedDate();
            if (receivedDate != null) {
                emailMessage.setReceivedDate(receivedDate.toInstant());
            } else {
                // Fallback to sent date if received date is not available
                Date sentDate = mimeMessage.getSentDate();
                if (sentDate != null) {
                    emailMessage.setReceivedDate(sentDate.toInstant());
                } else {
                    // Last resort: use current time
                    emailMessage.setReceivedDate(Instant.now());
                }
            }

            log.info("Successfully parsed email from S3 - Subject: {}, From: {}",
                    emailMessage.getSubject(), emailMessage.getFrom());

            return emailMessage;

        } catch (Exception e) {
            log.error("Failed to retrieve and parse email from S3. Bucket: {}, Key: {}",
                    bucketName, objectKey, e);
            throw new RuntimeException("Failed to retrieve and parse email from S3", e);
        }
    }

    private void publishEmailReceivedEvent(EmailMessage email) {
        log.debug("Publishing email received event for message: {}", email.getMessageId());
        eventPublisher.publishEvent(new EmailReceivedEvent(this, email));
    }
}
