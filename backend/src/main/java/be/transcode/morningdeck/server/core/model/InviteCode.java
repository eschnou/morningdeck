package be.transcode.morningdeck.server.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invite_codes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InviteCode {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(length = 255)
    private String description;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Builder.Default
    @Column(name = "use_count", nullable = false)
    private int useCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (code != null) {
            code = code.toUpperCase();
        }
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isUsable() {
        return enabled && !isExpired() && (maxUses == null || useCount < maxUses);
    }
}
