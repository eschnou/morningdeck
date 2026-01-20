package be.transcode.morningdeck.server.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicUserProfileDTO {
    private String id;
    private String username;
    private String name;
    private String avatarUrl;
}
