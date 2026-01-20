package be.transcode.morningdeck.server.provider.sourcefetch.reddit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RedditListingData {
    private String after;
    private String before;
    private List<RedditPostWrapper> children;
}
