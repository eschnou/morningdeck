package be.transcode.morningdeck.server.core.dto;

import be.transcode.morningdeck.server.core.model.Subscription;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUpdateSubscriptionDTO {
    @NotNull
    private Subscription.SubscriptionPlan plan;
}
