package be.transcode.morningdeck.server.core.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminAdjustCreditsDTO {
    @NotNull
    private Integer amount;

    @NotNull
    private CreditAdjustmentMode mode;

    public enum CreditAdjustmentMode {
        SET,  // Set absolute value
        ADD   // Add/subtract delta
    }
}
