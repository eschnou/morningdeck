package be.transcode.morningdeck.server.core.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VerificationResponse {
    private boolean success;
    private String message;
}
