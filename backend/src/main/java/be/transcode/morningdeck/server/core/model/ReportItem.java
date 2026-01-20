package be.transcode.morningdeck.server.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "report_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private DailyReport report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_item_id", nullable = false)
    private NewsItem newsItem;

    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false)
    private Integer position;
}
