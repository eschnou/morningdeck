package be.transcode.morningdeck.server.provider.emailreceive;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class EmailReceivedEvent extends ApplicationEvent {
    private final EmailMessage email;

    public EmailReceivedEvent(Object source, EmailMessage email) {
        super(source);
        this.email = email;
    }
}
