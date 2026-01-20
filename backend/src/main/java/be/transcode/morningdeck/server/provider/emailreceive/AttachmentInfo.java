package be.transcode.morningdeck.server.provider.emailreceive;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AttachmentInfo {
    private String filename;
    private String contentType;
    private byte[] content;
}
