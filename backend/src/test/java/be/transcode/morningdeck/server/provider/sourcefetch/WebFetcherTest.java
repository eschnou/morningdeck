package be.transcode.morningdeck.server.provider.sourcefetch;

import be.transcode.morningdeck.server.core.model.SourceType;
import be.transcode.morningdeck.server.core.util.HtmlToMarkdownConverter;
import be.transcode.morningdeck.server.provider.ai.AiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class WebFetcherTest {

    @Mock
    private HtmlToMarkdownConverter htmlToMarkdownConverter;

    @Mock
    private AiService aiService;

    private WebFetcher webFetcher;

    @BeforeEach
    void setUp() {
        webFetcher = new WebFetcher(htmlToMarkdownConverter, aiService);
    }

    @Test
    void shouldReturnWebSourceType() {
        assertThat(webFetcher.getSourceType()).isEqualTo(SourceType.WEB);
    }


    @Nested
    class UrlResolutionTests {

        @Test
        void shouldResolveRelativeUrls() {
            String baseUrl = "https://example.com/page";
            String relativeUrl = "/article/123";

            String resolved = UrlNormalizer.resolveRelative(baseUrl, relativeUrl);

            assertThat(resolved).isEqualTo("https://example.com/article/123");
        }

        @Test
        void shouldNormalizeUrlsForGuid() {
            String url = "https://EXAMPLE.COM/article/?utm_source=twitter";

            String normalized = UrlNormalizer.normalize(url);

            assertThat(normalized).isEqualTo("https://example.com/article");
        }
    }
}
