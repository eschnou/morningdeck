package be.transcode.morningdeck.server.core.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class SubscriptionStatusDTO {
    private UUID id;
    private String plan;
    private Integer creditsBalance;
    private Integer monthlyCredits;
    private Instant nextRenewalDate;
    private boolean autoRenew;
}
