package be.transcode.morningdeck.server.provider.emailsend;

import com.icegreen.greenmail.util.GreenMailUtil;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import be.transcode.morningdeck.server.config.TestcontainersConfiguration;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestcontainersConfiguration.class)
public class EmailServiceIT {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("user", "password"))
            .withPerMethodLifecycle(false);

    @Autowired
    private EmailService emailService;

    @Value("${application.email.test.recipient}")
    private String testRecipient;

    @Test
    void sendWelcomeEmail_ShouldActuallyDeliverEmail() throws Exception {
        // Arrange
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("username", "E2E Test User");
        parameters.put("loginUrl", "http://transcode.be/activate/e2e-test");

        // Act
        emailService.sendWelcomeEmail(testRecipient, "E2E Test User", "http://transcode.be/activate/e2e-test");

        // Assert - wait for email to arrive
        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
                    assertTrue(receivedMessages.length > 0, "No email received");

                    MimeMessage receivedMessage = receivedMessages[0];
                    assertEquals(testRecipient, receivedMessage.getAllRecipients()[0].toString());
                    assertEquals("Welcome to Morning Deck", receivedMessage.getSubject());

                    // Verify content
                    String content = GreenMailUtil.getBody(receivedMessage);
                    assertTrue(content.contains("E2E Test User"));
                    assertTrue(content.contains("http://transcode.be/activate/e2e-test"));
                });
    }
}
