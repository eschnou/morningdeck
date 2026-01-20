package be.transcode.morningdeck.server.provider.webfetch;

import be.transcode.morningdeck.server.core.util.HtmlToMarkdownConverter;
import lombok.extern.slf4j.Slf4j;
import net.dankito.readability4j.Readability4J;
import net.dankito.readability4j.Article;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * HTTP-based implementation of WebContentFetcher using Java HttpClient and Readability4J.
 * Fetches web pages, extracts article content, and converts to markdown.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "application.web-fetch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HttpClientWebFetcher implements WebContentFetcher {

    private final HtmlToMarkdownConverter markdownConverter;
    private final HttpClient httpClient;
    private final int timeoutSeconds;
    private final String userAgent;
    private final boolean allowLocalhost;

    // Pattern to match private IP ranges
    private static final Pattern PRIVATE_IP_PATTERN = Pattern.compile(
            "^(127\\.|10\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|192\\.168\\.|169\\.254\\.|0\\.|::1|fc|fd)"
    );

    @Autowired
    public HttpClientWebFetcher(
            HtmlToMarkdownConverter markdownConverter,
            @Value("${application.web-fetch.timeout-seconds:15}") int timeoutSeconds,
            @Value("${application.web-fetch.user-agent:MorningDeck/1.0}") String userAgent) {
        this(markdownConverter, timeoutSeconds, userAgent, false);
    }

    /**
     * Constructor with allowLocalhost flag for testing purposes.
     */
    HttpClientWebFetcher(
            HtmlToMarkdownConverter markdownConverter,
            int timeoutSeconds,
            String userAgent,
            boolean allowLocalhost) {
        this.markdownConverter = markdownConverter;
        this.timeoutSeconds = timeoutSeconds;
        this.userAgent = userAgent;
        this.allowLocalhost = allowLocalhost;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        log.info("Web content fetching enabled with timeout={}s, allowLocalhost={}", timeoutSeconds, allowLocalhost);
    }

    @Override
    public Optional<String> fetch(String url) {
        if (!isValidUrl(url)) {
            log.debug("Skipping invalid URL: {}", url);
            return Optional.empty();
        }

        try {
            log.debug("Fetching web content from: {}", url);
            String html = fetchHtml(url);

            if (html == null || html.isBlank()) {
                log.warn("Empty response from URL: {}", url);
                return Optional.empty();
            }

            String articleHtml = extractArticle(url, html);

            if (articleHtml == null || articleHtml.isBlank()) {
                log.warn("No article content extracted from URL: {}", url);
                return Optional.empty();
            }

            String markdown = markdownConverter.convert(articleHtml);
            log.info("Successfully fetched web content from {} ({} chars)", url, markdown.length());
            return Optional.of(markdown);

        } catch (Exception e) {
            log.warn("Failed to fetch web content from {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Validates URL for safety (SSRF prevention) and correctness.
     */
    boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        // Must be HTTP or HTTPS
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            log.debug("Rejecting non-HTTP URL: {}", url);
            return false;
        }

        try {
            URI uri = URI.create(url);
            String host = uri.getHost();

            if (host == null || host.isBlank()) {
                return false;
            }

            // Skip SSRF checks if localhost is allowed (testing mode)
            if (allowLocalhost && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1"))) {
                return true;
            }

            // Reject localhost
            if (host.equalsIgnoreCase("localhost")) {
                log.debug("Rejecting localhost URL: {}", url);
                return false;
            }

            // Check for private IP addresses
            if (isPrivateIp(host)) {
                log.debug("Rejecting private IP URL: {}", url);
                return false;
            }

            // Validate port (only 80, 443, or default)
            int port = uri.getPort();
            if (port != -1 && port != 80 && port != 443) {
                log.debug("Rejecting non-standard port URL: {}", url);
                return false;
            }

            return true;

        } catch (Exception e) {
            log.debug("Invalid URL format: {} - {}", url, e.getMessage());
            return false;
        }
    }

    private boolean isPrivateIp(String host) {
        // Direct pattern match for IP addresses
        if (PRIVATE_IP_PATTERN.matcher(host).find()) {
            return true;
        }

        // Try to resolve hostname and check if it resolves to a private IP
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress();
        } catch (Exception e) {
            // If we can't resolve, allow it (will fail at fetch time if invalid)
            return false;
        }
    }

    private String fetchHtml(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP error: " + response.statusCode());
        }

        return response.body();
    }

    private String extractArticle(String url, String html) {
        Readability4J readability = new Readability4J(url, html);
        Article article = readability.parse();
        return article.getContent();
    }
}
