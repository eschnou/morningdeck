package be.transcode.morningdeck.server.provider.emailreceive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmailMessage {
    private String messageId;
    private String from;
    private List<String> to;
    private String subject;
    private String content;
    private Instant receivedDate;
    private List<AttachmentInfo> attachments;
}
