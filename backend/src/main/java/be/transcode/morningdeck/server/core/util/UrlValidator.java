package be.transcode.morningdeck.server.core.util;

import be.transcode.morningdeck.server.core.exception.SourceValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Set;

@Component
public class UrlValidator {

    private static final Set<String> ALLOWED_PROTOCOLS = Set.of("http", "https");
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "169.254.169.254"
    );

    @Value("${app.security.allow-localhost:false}")
    private boolean allowLocalhost;

    public void validate(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw new SourceValidationException("URL is required");
        }

        try {
            URL url = new URL(urlString);

            if (!ALLOWED_PROTOCOLS.contains(url.getProtocol().toLowerCase())) {
                throw new SourceValidationException("Only HTTP/HTTPS protocols allowed");
            }

            String host = url.getHost().toLowerCase();

            // Skip localhost checks in test mode
            if (!allowLocalhost) {
                if (BLOCKED_HOSTS.contains(host) || host.endsWith(".internal") || host.endsWith(".local")) {
                    throw new SourceValidationException("Invalid host");
                }

                // Check for private IP ranges
                InetAddress address = InetAddress.getByName(host);
                if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                    throw new SourceValidationException("Private IP addresses not allowed");
                }
            }
        } catch (MalformedURLException e) {
            throw new SourceValidationException("Invalid URL format: " + e.getMessage());
        } catch (UnknownHostException e) {
            throw new SourceValidationException("Unknown host: " + e.getMessage());
        }
    }
}
