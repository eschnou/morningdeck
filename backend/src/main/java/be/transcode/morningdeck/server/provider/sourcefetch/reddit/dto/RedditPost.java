package be.transcode.morningdeck.server.provider.sourcefetch.reddit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RedditPost {
    private String id;
    private String name;
    private String title;
    private String author;
    private String url;
    private String permalink;
    private String selftext;
    private String subreddit;
    private String domain;

    @JsonProperty("is_self")
    private boolean isSelf;

    @JsonProperty("created_utc")
    private double createdUtc;

    private int score;

    @JsonProperty("num_comments")
    private int numComments;

    @JsonProperty("over_18")
    private boolean over18;

    private boolean stickied;
}
