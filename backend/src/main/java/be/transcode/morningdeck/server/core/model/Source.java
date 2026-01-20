package be.transcode.morningdeck.server.core.model;

import be.transcode.morningdeck.server.core.util.StringListConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sources", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"day_brief_id", "url"})
})
@Data
@EqualsAndHashCode(exclude = {"newsItems", "dayBrief"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Source {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_brief_id", nullable = false)
    private DayBrief dayBrief;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SourceStatus status = SourceStatus.ACTIVE;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> tags;

    @Column(name = "last_fetched_at")
    private Instant lastFetchedAt;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "etag")
    private String etag;

    @Column(name = "last_modified")
    private String lastModified;

    @Enumerated(EnumType.STRING)
    @Column(name = "fetch_status", nullable = false)
    @Builder.Default
    private FetchStatus fetchStatus = FetchStatus.IDLE;

    @Column(name = "refresh_interval_minutes", nullable = false)
    @Builder.Default
    private Integer refreshIntervalMinutes = 15;

    @Column(name = "queued_at")
    private Instant queuedAt;

    @Column(name = "fetch_started_at")
    private Instant fetchStartedAt;

    @Column(name = "email_address", unique = true)
    private UUID emailAddress;

    @Column(name = "extraction_prompt", length = 2048)
    private String extractionPrompt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NewsItem> newsItems = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Helper to get user ID via the parent briefing.
     */
    public UUID getUserId() {
        return dayBrief != null ? dayBrief.getUserId() : null;
    }
}
