package be.transcode.morningdeck.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "application")
@Getter
@Setter
public class AppConfig {
    private String rootDomain;
    private String rootUrl;
    private String logFolder;
    private String uploadFolder;
    private boolean closedBeta;
}
