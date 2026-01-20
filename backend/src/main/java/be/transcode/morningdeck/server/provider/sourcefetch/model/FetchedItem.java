package be.transcode.morningdeck.server.provider.sourcefetch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchedItem {
    private String guid;
    private String title;
    private String link;
    private String author;
    private Instant publishedAt;
    private String rawContent;
    private String cleanContent;
}
