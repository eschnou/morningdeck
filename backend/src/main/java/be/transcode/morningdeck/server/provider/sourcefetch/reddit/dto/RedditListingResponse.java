package be.transcode.morningdeck.server.provider.sourcefetch.reddit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RedditListingResponse {
    private String kind;
    private RedditListingData data;
}
