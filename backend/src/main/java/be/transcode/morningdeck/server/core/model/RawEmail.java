package be.transcode.morningdeck.server.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores raw email content for audit and debugging purposes.
 */
@Entity
@Table(name = "raw_emails", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"source_id", "message_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RawEmail {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(name = "message_id", nullable = false, length = 512)
    private String messageId;

    @Column(name = "from_address", nullable = false, length = 512)
    private String fromAddress;

    @Column(nullable = false, length = 1024)
    private String subject;

    @Column(name = "raw_content", columnDefinition = "TEXT", nullable = false)
    private String rawContent;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
