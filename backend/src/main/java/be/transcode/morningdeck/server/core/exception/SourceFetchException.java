package be.transcode.morningdeck.server.core.exception;

public class SourceFetchException extends RuntimeException {
    public SourceFetchException(String message) {
        super(message);
    }

    public SourceFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
