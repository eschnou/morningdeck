package be.transcode.morningdeck.server.core.model;

import be.transcode.morningdeck.server.provider.ai.AiFeature;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity for tracking AI API usage.
 * Captures timing, token usage, feature attribution, and user association.
 */
@Entity
@Table(name = "api_usage_logs", indexes = {
    @Index(name = "idx_api_usage_user_id", columnList = "user_id"),
    @Index(name = "idx_api_usage_feature", columnList = "feature_key"),
    @Index(name = "idx_api_usage_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_key", nullable = false, length = 32)
    private AiFeature featureKey;

    @Column(length = 64)
    private String model;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    @Column(nullable = false)
    private Boolean success;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
