package be.transcode.morningdeck.server.core.exception;

public class SourceValidationException extends BadRequestException {
    public SourceValidationException(String message) {
        super(message);
    }
}
