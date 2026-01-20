package be.transcode.morningdeck.server.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminResetPasswordDTO {
    @NotBlank
    @Size(min = 8)
    private String newPassword;
}
