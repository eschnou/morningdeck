package be.transcode.morningdeck.server.provider.emailsend;

import static org.junit.jupiter.api.Assertions.*;

import be.transcode.morningdeck.server.provider.emailsend.exceptions.EmailPreparationException;
import be.transcode.morningdeck.server.provider.emailsend.model.Email;
import be.transcode.morningdeck.server.provider.emailsend.sender.EmailSender;
import be.transcode.morningdeck.server.provider.emailsend.templating.EmailTemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private EmailSender emailSender;

    @Mock
    private EmailTemplateEngine templateEngine;

    private EmailService emailService;

    private static final String DEFAULT_FROM_ADDRESS = "noreply@example.com";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_TEMPLATE_CONTENT = "Welcome Content";

    @BeforeEach
    void setUp() {
        emailService = new EmailService(emailSender, templateEngine, "Morning Deck");
        ReflectionTestUtils.setField(emailService, "defaultFromAddress", DEFAULT_FROM_ADDRESS);
    }

    @Test
    void sendWelcomeEmail_ShouldSendEmailSuccessfully() {
        // Arrange
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("fullName", "John");
        parameters.put("domain", "https://example.com");

        when(templateEngine.processTemplate(eq("welcome"), any()))
                .thenReturn(TEST_TEMPLATE_CONTENT);

        // Act
        emailService.sendWelcomeEmail(TEST_EMAIL, "John", "https://example.com");

        // Assert
        ArgumentCaptor<Email> emailCaptor = ArgumentCaptor.forClass(Email.class);
        verify(emailSender).send(emailCaptor.capture());
        verify(templateEngine).processTemplate(eq("welcome"), eq(parameters));

        Email capturedEmail = emailCaptor.getValue();
        assertEquals(TEST_EMAIL, capturedEmail.getTo());
        assertEquals("Morning Deck <" + DEFAULT_FROM_ADDRESS + ">", capturedEmail.getFrom());
        assertEquals("Welcome to Morning Deck", capturedEmail.getSubject());
        assertEquals(TEST_TEMPLATE_CONTENT, capturedEmail.getContent());
    }

    @Test
    void sendWelcomeEmail_WhenTemplateProcessingFails_ShouldThrowEmailPreparationException() {
        // Arrange
        Map<String, Object> parameters = new HashMap<>();
        when(templateEngine.processTemplate(any(), any()))
                .thenThrow(new RuntimeException("Template processing failed"));

        // Act & Assert
        assertThrows(EmailPreparationException.class, () ->
                emailService.sendWelcomeEmail(TEST_EMAIL, "John", "https://example.com")
        );
        verify(emailSender, never()).send(any());
    }

    @Test
    void sendWelcomeEmail_WithNullName_ShouldFail() {
        // Act
        assertThrows(IllegalArgumentException.class, () ->
                emailService.sendWelcomeEmail(TEST_EMAIL, null, null)
        );

        // Assert
        verify(emailSender, never()).send(any());
    }
}
