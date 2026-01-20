package be.transcode.morningdeck.server.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "day_briefs")
@Data
@EqualsAndHashCode(exclude = {"sources"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DayBrief {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    @Column(length = 1024)
    private String description;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String briefing;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BriefingFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_day_of_week")
    private DayOfWeek scheduleDayOfWeek;

    @Column(name = "schedule_time", nullable = false)
    private LocalTime scheduleTime;

    @Column(nullable = false)
    @Builder.Default
    private String timezone = "UTC";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DayBriefStatus status = DayBriefStatus.ACTIVE;

    @Column(name = "last_executed_at")
    private Instant lastExecutedAt;

    @Column(name = "queued_at")
    private Instant queuedAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "email_delivery_enabled", nullable = false)
    @Builder.Default
    private boolean emailDeliveryEnabled = true;

    @Column(nullable = false)
    private Integer position;

    @OneToMany(mappedBy = "dayBrief", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Source> sources = new ArrayList<>();

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

    public List<UUID> getSourceIds() {
        return sources.stream().map(Source::getId).toList();
    }
}
