package be.transcode.morningdeck.server.provider.emailsend.model;

public enum EmailFormat {
    PLAIN_TEXT("text/plain"),
    HTML("text/html");

    private final String mimeType;

    EmailFormat(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMimeType() {
        return mimeType;
    }
}
