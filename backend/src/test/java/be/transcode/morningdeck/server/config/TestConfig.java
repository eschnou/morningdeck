package be.transcode.morningdeck.server.config;

import be.transcode.morningdeck.server.provider.emailsend.sender.EmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public SqsAsyncClient mockSqsAsyncClient() {
        return mock(SqsAsyncClient.class);
    }

    @Bean
    @Primary
    public EmailSender mockEmailSender() {
        return mock(EmailSender.class);
    }
}
