package be.transcode.morningdeck.server.provider.emailsend.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EmailAttachment {
    String filename;
    byte[] content;
    String contentType;

    @Builder.Default
    AttachmentDisposition disposition = AttachmentDisposition.ATTACHMENT;
}
