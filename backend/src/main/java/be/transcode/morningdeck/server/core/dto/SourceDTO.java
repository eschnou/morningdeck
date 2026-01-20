package be.transcode.morningdeck.server.core.dto;

import be.transcode.morningdeck.server.core.model.FetchStatus;
import be.transcode.morningdeck.server.core.model.SourceStatus;
import be.transcode.morningdeck.server.core.model.SourceType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SourceDTO {
    private UUID id;

    private UUID briefingId;
    private String briefingTitle;

    private String url;
    private String extractionPrompt;

    private String name;
    private String emailAddress;
    private SourceType type;
    private SourceStatus status;
    private List<String> tags;
    private Instant lastFetchedAt;
    private String lastError;
    private FetchStatus fetchStatus;
    private Integer refreshIntervalMinutes;
    private Long itemCount;
    private Long unreadCount;
    private Instant createdAt;
}
