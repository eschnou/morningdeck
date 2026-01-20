package be.transcode.morningdeck.server.provider.sourcefetch.reddit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "application.reddit")
public class RedditConfig {
    private String clientId;
    private String clientSecret;
    private String userAgent = "MorningDeck/1.0";
    private int defaultLimit = 25;
    private int maxAgeHours = 24;
}
