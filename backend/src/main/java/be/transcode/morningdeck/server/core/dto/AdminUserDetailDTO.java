package be.transcode.morningdeck.server.core.dto;

import be.transcode.morningdeck.server.core.model.Language;
import be.transcode.morningdeck.server.core.model.Role;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class AdminUserDetailDTO {
    private String id;
    private String username;
    private String email;
    private String name;
    private String avatarUrl;
    private Language language;
    private Role role;
    private boolean enabled;
    private boolean emailVerified;
    private Instant createdAt;
    private SubscriptionStatusDTO subscription;
}
