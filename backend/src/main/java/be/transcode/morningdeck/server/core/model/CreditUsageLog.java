package be.transcode.morningdeck.server.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_usage_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditUsageLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Column(nullable = false)
    private Integer creditsUsed;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    @PrePersist
    protected void onCreate() {
        usedAt = Instant.now();
    }
}
