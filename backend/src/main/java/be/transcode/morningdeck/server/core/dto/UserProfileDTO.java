package be.transcode.morningdeck.server.core.dto;

import be.transcode.morningdeck.server.core.model.Language;
import be.transcode.morningdeck.server.core.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private String id;
    private String username;
    private String name;
    private String email;
    private Language language;
    private String avatarUrl;
    private Role role;
}
