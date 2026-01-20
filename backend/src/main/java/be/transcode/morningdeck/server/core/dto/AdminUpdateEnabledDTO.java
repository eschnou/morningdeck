package be.transcode.morningdeck.server.core.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUpdateEnabledDTO {
    @NotNull
    private Boolean enabled;
}
