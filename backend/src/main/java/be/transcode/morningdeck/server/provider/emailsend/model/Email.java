package be.transcode.morningdeck.server.provider.emailsend.model;

import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@Value
@Builder
public class Email {
    String to;
    String from;
    String subject;
    String content;
    @Builder.Default
    List<EmailAttachment> attachments = new ArrayList<>();

    @Builder.Default
    EmailFormat format = EmailFormat.HTML;

    public boolean hasAttachments() {
        return attachments != null && !attachments.isEmpty();
    }
}
