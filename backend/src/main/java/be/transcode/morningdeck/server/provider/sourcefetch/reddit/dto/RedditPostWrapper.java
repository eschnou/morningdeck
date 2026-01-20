package be.transcode.morningdeck.server.provider.sourcefetch.reddit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RedditPostWrapper {
    private String kind;
    private RedditPost data;
}
