package be.transcode.morningdeck.server.provider.sourcefetch;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlNormalizerTest {

    @Nested
    class NormalizeTests {

        @Test
        void shouldLowercaseHostname() {
            String result = UrlNormalizer.normalize("https://EXAMPLE.COM/path");
            assertThat(result).isEqualTo("https://example.com/path");
        }

        @Test
        void shouldRemoveTrailingSlash() {
            String result = UrlNormalizer.normalize("https://example.com/path/");
            assertThat(result).isEqualTo("https://example.com/path");
        }

        @Test
        void shouldPreserveRootPath() {
            String result = UrlNormalizer.normalize("https://example.com/");
            assertThat(result).isEqualTo("https://example.com/");
        }

        @Test
        void shouldRemoveUtmParameters() {
            String result = UrlNormalizer.normalize(
                    "https://example.com/article?utm_source=twitter&utm_medium=social&id=123");
            assertThat(result).isEqualTo("https://example.com/article?id=123");
        }

        @Test
        void shouldRemoveAllTrackingParams() {
            String result = UrlNormalizer.normalize(
                    "https://example.com/page?fbclid=abc&ref=homepage&gclid=xyz");
            assertThat(result).isEqualTo("https://example.com/page");
        }

        @Test
        void shouldPreserveNonTrackingQueryParams() {
            String result = UrlNormalizer.normalize("https://example.com/search?q=test&page=2");
            assertThat(result).isEqualTo("https://example.com/search?q=test&page=2");
        }

        @Test
        void shouldHandleUrlWithNoQueryParams() {
            String result = UrlNormalizer.normalize("https://example.com/article/123");
            assertThat(result).isEqualTo("https://example.com/article/123");
        }

        @Test
        void shouldHandleNullUrl() {
            String result = UrlNormalizer.normalize(null);
            assertThat(result).isNull();
        }

        @Test
        void shouldHandleBlankUrl() {
            String result = UrlNormalizer.normalize("   ");
            assertThat(result).isEqualTo("   ");
        }

        @Test
        void shouldPreservePort() {
            String result = UrlNormalizer.normalize("https://example.com:8080/path");
            assertThat(result).isEqualTo("https://example.com:8080/path");
        }

        @Test
        void shouldPreserveFragment() {
            String result = UrlNormalizer.normalize("https://example.com/page#section");
            assertThat(result).isEqualTo("https://example.com/page#section");
        }
    }

    @Nested
    class ResolveRelativeTests {

        @Test
        void shouldReturnAbsoluteUrlUnchanged() {
            String result = UrlNormalizer.resolveRelative(
                    "https://example.com", "https://other.com/page");
            assertThat(result).isEqualTo("https://other.com/page");
        }

        @Test
        void shouldResolveRootRelativePath() {
            String result = UrlNormalizer.resolveRelative(
                    "https://example.com/dir/page", "/other/article");
            assertThat(result).isEqualTo("https://example.com/other/article");
        }

        @Test
        void shouldResolveRelativePath() {
            String result = UrlNormalizer.resolveRelative(
                    "https://example.com/dir/page", "article");
            assertThat(result).isEqualTo("https://example.com/dir/article");
        }

        @Test
        void shouldResolveParentRelativePath() {
            String result = UrlNormalizer.resolveRelative(
                    "https://example.com/dir/subdir/page", "../article");
            assertThat(result).isEqualTo("https://example.com/dir/article");
        }

        @Test
        void shouldResolveProtocolRelativeUrl() {
            String result = UrlNormalizer.resolveRelative(
                    "https://example.com/page", "//cdn.example.com/image.png");
            assertThat(result).isEqualTo("https://cdn.example.com/image.png");
        }

        @Test
        void shouldHandleNullRelativeUrl() {
            String result = UrlNormalizer.resolveRelative("https://example.com", null);
            assertThat(result).isNull();
        }

        @Test
        void shouldHandleBlankRelativeUrl() {
            String result = UrlNormalizer.resolveRelative("https://example.com", "  ");
            assertThat(result).isEqualTo("  ");
        }
    }
}
