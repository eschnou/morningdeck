package be.transcode.morningdeck.server.core.dto;

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
public class ReportItemDTO {
    private UUID newsItemId;
    private String title;
    private String summary;
    private String link;
    private Instant publishedAt;
    private Integer score;
    private Integer position;
    private String sourceName;
}
