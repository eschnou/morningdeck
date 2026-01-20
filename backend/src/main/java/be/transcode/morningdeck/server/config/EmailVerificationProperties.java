package be.transcode.morningdeck.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "application.email.verification")
@Data
public class EmailVerificationProperties {
    private boolean enabled = true;
    private int expirationHours = 24;
}
