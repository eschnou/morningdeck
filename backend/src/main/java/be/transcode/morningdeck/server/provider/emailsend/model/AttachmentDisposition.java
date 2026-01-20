package be.transcode.morningdeck.server.provider.emailsend.model;

public enum AttachmentDisposition {
    INLINE("inline"),
    ATTACHMENT("attachment");

    private final String value;

    AttachmentDisposition(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
