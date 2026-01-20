package be.transcode.morningdeck.server.core.dto;

import be.transcode.morningdeck.server.core.model.NewsItemTags;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NewsItemDTO {
    private UUID id;
    private String title;
    private String link;
    private String author;
    private Instant publishedAt;
    private String content;
    private String summary;
    private NewsItemTags tags;
    private UUID sourceId;
    private String sourceName;
    private Instant readAt;
    private Boolean saved;
    private Integer score;
    private String scoreReasoning;
    private Instant createdAt;
}
