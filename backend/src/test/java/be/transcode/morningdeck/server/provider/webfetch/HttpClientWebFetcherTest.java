package be.transcode.morningdeck.server.provider.webfetch;

import be.transcode.morningdeck.server.core.util.HtmlToMarkdownConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HttpClientWebFetcher Unit Tests")
class HttpClientWebFetcherTest {

    private HttpClientWebFetcher fetcher;

    @BeforeEach
    void setUp() {
        HtmlToMarkdownConverter markdownConverter = new HtmlToMarkdownConverter();
        fetcher = new HttpClientWebFetcher(markdownConverter, 15, "DayBrief/1.0");
    }

    @Test
    @DisplayName("Should return empty for null URL")
    void shouldReturnEmptyForNullUrl() {
        assertThat(fetcher.fetch(null)).isEmpty();
    }

    @Test
    @DisplayName("Should return empty for blank URL")
    void shouldReturnEmptyForBlankUrl() {
        assertThat(fetcher.fetch("")).isEmpty();
        assertThat(fetcher.fetch("  ")).isEmpty();
    }

    @Test
    @DisplayName("Should return empty for mailto URL")
    void shouldReturnEmptyForMailtoUrl() {
        assertThat(fetcher.fetch("mailto:test@example.com")).isEmpty();
    }

    @Test
    @DisplayName("Should return empty for ftp URL")
    void shouldReturnEmptyForFtpUrl() {
        assertThat(fetcher.fetch("ftp://files.example.com/doc.pdf")).isEmpty();
    }

    @Test
    @DisplayName("Should reject localhost URL")
    void shouldRejectLocalhostUrl() {
        assertThat(fetcher.isValidUrl("http://localhost/article")).isFalse();
        assertThat(fetcher.isValidUrl("http://localhost:8080/article")).isFalse();
        assertThat(fetcher.isValidUrl("https://localhost/article")).isFalse();
    }

    @Test
    @DisplayName("Should reject loopback IP")
    void shouldRejectLoopbackIp() {
        assertThat(fetcher.isValidUrl("http://127.0.0.1/article")).isFalse();
        assertThat(fetcher.isValidUrl("http://127.0.0.1:8080/article")).isFalse();
    }

    @Test
    @DisplayName("Should reject private IP 10.x.x.x")
    void shouldRejectPrivateIp10() {
        assertThat(fetcher.isValidUrl("http://10.0.0.1/article")).isFalse();
        assertThat(fetcher.isValidUrl("http://10.255.255.255/article")).isFalse();
    }

    @Test
    @DisplayName("Should reject private IP 172.16-31.x.x")
    void shouldRejectPrivateIp172() {
        assertThat(fetcher.isValidUrl("http://172.16.0.1/article")).isFalse();
        assertThat(fetcher.isValidUrl("http://172.31.255.255/article")).isFalse();
    }

    @Test
    @DisplayName("Should reject private IP 192.168.x.x")
    void shouldRejectPrivateIp192() {
        assertThat(fetcher.isValidUrl("http://192.168.0.1/article")).isFalse();
        assertThat(fetcher.isValidUrl("http://192.168.1.1/article")).isFalse();
    }

    @Test
    @DisplayName("Should reject link-local IP 169.254.x.x")
    void shouldRejectLinkLocalIp() {
        assertThat(fetcher.isValidUrl("http://169.254.0.1/article")).isFalse();
    }

    @Test
    @DisplayName("Should reject non-standard ports")
    void shouldRejectNonStandardPorts() {
        assertThat(fetcher.isValidUrl("http://example.com:8080/article")).isFalse();
        assertThat(fetcher.isValidUrl("http://example.com:3000/article")).isFalse();
    }

    @Test
    @DisplayName("Should accept valid HTTP URLs")
    void shouldAcceptValidHttpUrls() {
        assertThat(fetcher.isValidUrl("http://example.com/article")).isTrue();
        assertThat(fetcher.isValidUrl("http://example.com:80/article")).isTrue();
    }

    @Test
    @DisplayName("Should accept valid HTTPS URLs")
    void shouldAcceptValidHttpsUrls() {
        assertThat(fetcher.isValidUrl("https://example.com/article")).isTrue();
        assertThat(fetcher.isValidUrl("https://example.com:443/article")).isTrue();
        assertThat(fetcher.isValidUrl("https://news.ycombinator.com/item?id=123")).isTrue();
    }

    @Test
    @DisplayName("Should accept public IP addresses")
    void shouldAcceptPublicIpAddresses() {
        assertThat(fetcher.isValidUrl("http://93.184.216.34/article")).isTrue();
        assertThat(fetcher.isValidUrl("https://8.8.8.8/")).isTrue();
    }
}
