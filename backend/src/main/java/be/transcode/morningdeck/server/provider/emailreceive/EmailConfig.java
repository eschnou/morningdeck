package be.transcode.morningdeck.server.provider.emailreceive;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "mail")
public class EmailConfig {
    private String host = "localhost";
    private String username;
    private String password;
    private String protocol = "imaps"; // "imaps" or "pop3s"
    private int port = 993;
    private String folder = "INBOX";
    private boolean enableSsl = true;
}
