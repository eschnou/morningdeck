package be.transcode.morningdeck.server.core.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class InsufficientCreditsException extends RuntimeException {
    private final UUID userId;
    private final int creditsRequired;
    private final int creditsAvailable;

    public InsufficientCreditsException(UUID userId, int creditsRequired, int creditsAvailable) {
        super(String.format("Insufficient credits for user %s: required=%d, available=%d",
                userId, creditsRequired, creditsAvailable));
        this.userId = userId;
        this.creditsRequired = creditsRequired;
        this.creditsAvailable = creditsAvailable;
    }
}
