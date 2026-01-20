package be.transcode.morningdeck.server.core.exception;

public class DuplicateSourceException extends BadRequestException {
    public DuplicateSourceException(String url) {
        super("Source with URL already exists: " + url);
    }
}
