package be.transcode.morningdeck.server.core.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ReorderBriefsRequest {
    @NotNull
    @Size(min = 1, message = "At least one brief ID is required")
    private List<UUID> briefIds;
}
