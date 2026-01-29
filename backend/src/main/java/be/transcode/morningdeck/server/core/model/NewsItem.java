package be.transcode.morningdeck.server.core.model;

import be.transcode.morningdeck.server.core.util.NewsItemTagsConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "news_items", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"source_id", "guid"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(nullable = false, length = 512)
    private String guid;

    @Column(nullable = false, length = 1024)
    private String title;

    @Column(nullable = false, length = 4096)
    private String link;

    @Column
    private String author;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "clean_content", columnDefinition = "TEXT")
    private String cleanContent;

    @Column(name = "web_content", columnDefinition = "TEXT")
    private String webContent;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Convert(converter = NewsItemTagsConverter.class)
    @Column(columnDefinition = "TEXT")
    private NewsItemTags tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NewsItemStatus status = NewsItemStatus.NEW;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "saved", nullable = false)
    @Builder.Default
    private Boolean saved = false;

    @Column(name = "score")
    private Integer score;

    @Column(name = "score_reasoning", length = 512)
    private String scoreReasoning;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
