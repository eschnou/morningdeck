package be.transcode.morningdeck.server.provider.emailsend.exceptions;

public class EmailPreparationException extends RuntimeException {
    public EmailPreparationException(String message) {
        super(message);
    }

    public EmailPreparationException(String message, Throwable cause) {
        super(message, cause);
    }
}
