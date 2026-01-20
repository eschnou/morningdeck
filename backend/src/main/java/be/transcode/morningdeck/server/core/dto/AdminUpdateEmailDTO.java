package be.transcode.morningdeck.server.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminUpdateEmailDTO {
    @NotBlank
    @Email
    private String email;
}
