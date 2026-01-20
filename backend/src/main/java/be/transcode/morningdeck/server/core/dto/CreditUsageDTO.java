package be.transcode.morningdeck.server.core.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CreditUsageDTO {
    private UUID id;
    private Integer creditsUsed;
    private Instant usedAt;
}
