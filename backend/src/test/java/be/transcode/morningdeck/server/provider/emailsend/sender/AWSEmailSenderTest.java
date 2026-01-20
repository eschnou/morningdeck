package be.transcode.morningdeck.server.provider.emailsend.sender;

import be.transcode.morningdeck.server.provider.emailsend.exceptions.EmailSendException;
import be.transcode.morningdeck.server.provider.emailsend.model.Email;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AWSEmailSenderTest {

    @Mock
    private SesClient sesClient;

    @InjectMocks
    private AWSEmailSender awsEmailSender;

    @Test
    void sendEmailSuccessfully() throws EmailSendException {
        Email email = Email.builder()
                .to("test@example.com")
                .from("noreply@example.com")
                .subject("Test Email")
                .content("Test email content")
                .build();

        awsEmailSender.send(email);

        verify(sesClient, times(1)).sendEmail(any(SendEmailRequest.class));
    }


    @Test
    void sendEmailFailure() {
        Email email = Email.builder()
                .to("test@example.com")
                .from("noreply@example.com")
                .subject("Test Email")
                .content("Test email content")
                .build();

        AwsServiceException exception = SesException.builder().message("Failed to send").build();

        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenThrow(exception);


        try {
            awsEmailSender.send(email);
        } catch (EmailSendException e) {
            assert true;
            return;
        }

        assert false;


    }
}
