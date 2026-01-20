package be.transcode.morningdeck.server.core.exception;

public class AiProcessingException extends RuntimeException {
    public AiProcessingException(String message) {
        super(message);
    }

    public AiProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
