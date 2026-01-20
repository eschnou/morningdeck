package be.transcode.morningdeck.server.provider.webfetch;

import be.transcode.morningdeck.server.core.util.HtmlToMarkdownConverter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebFetch Integration Tests")
class WebFetchIT {

    private static WireMockServer wireMockServer;
    private HttpClientWebFetcher fetcher;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        HtmlToMarkdownConverter markdownConverter = new HtmlToMarkdownConverter();
        // Use allowLocalhost=true for testing with WireMock
        fetcher = new HttpClientWebFetcher(markdownConverter, 5, "DayBrief-Test/1.0", true);
    }

    @Test
    @DisplayName("Should fetch and extract article content")
    void shouldFetchAndExtractArticleContent() {
        String articleHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Test Article</title></head>
            <body>
                <nav>Navigation menu</nav>
                <article>
                    <h1>Test Article Title</h1>
                    <p>This is the main article content. It contains important information that should be extracted.</p>
                    <p>This is another paragraph with more details about the topic at hand.</p>
                </article>
                <footer>Footer content</footer>
            </body>
            </html>
            """;

        stubFor(get(urlEqualTo("/article"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody(articleHtml)));

        String url = wireMockServer.baseUrl() + "/article";
        Optional<String> result = fetcher.fetch(url);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("Test Article Title");
        assertThat(result.get()).contains("main article content");
    }

    @Test
    @DisplayName("Should handle redirects")
    void shouldHandleRedirects() {
        String articleHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <article>
                    <h1>Redirected Article</h1>
                    <p>This content was found after a redirect.</p>
                </article>
            </body>
            </html>
            """;

        stubFor(get(urlEqualTo("/old-path"))
                .willReturn(aResponse()
                        .withStatus(301)
                        .withHeader("Location", wireMockServer.baseUrl() + "/new-path")));

        stubFor(get(urlEqualTo("/new-path"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody(articleHtml)));

        String url = wireMockServer.baseUrl() + "/old-path";
        Optional<String> result = fetcher.fetch(url);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("Redirected Article");
    }

    @Test
    @DisplayName("Should return empty on 404")
    void shouldReturnEmptyOn404() {
        stubFor(get(urlEqualTo("/not-found"))
                .willReturn(aResponse()
                        .withStatus(404)));

        String url = wireMockServer.baseUrl() + "/not-found";
        Optional<String> result = fetcher.fetch(url);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty on 500")
    void shouldReturnEmptyOn500() {
        stubFor(get(urlEqualTo("/error"))
                .willReturn(aResponse()
                        .withStatus(500)));

        String url = wireMockServer.baseUrl() + "/error";
        Optional<String> result = fetcher.fetch(url);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should handle timeout gracefully")
    void shouldHandleTimeoutGracefully() {
        stubFor(get(urlEqualTo("/slow"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(10000) // 10 second delay, timeout is 5s
                        .withBody("<html><body><p>Slow response</p></body></html>")));

        String url = wireMockServer.baseUrl() + "/slow";
        Optional<String> result = fetcher.fetch(url);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should extract content from complex HTML")
    void shouldExtractContentFromComplexHtml() {
        String complexHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>News Article</title>
                <script>var x = 1;</script>
                <style>.ad { display: block; }</style>
            </head>
            <body>
                <header>
                    <nav><a href="/">Home</a><a href="/news">News</a></nav>
                </header>
                <aside class="sidebar">
                    <div class="ad">Advertisement</div>
                </aside>
                <main>
                    <article>
                        <h1>Breaking News Story</h1>
                        <p class="byline">By John Doe</p>
                        <p>The main story begins here with important facts and details that readers need to know.</p>
                        <p>Additional paragraphs provide context and background information about the situation.</p>
                        <blockquote>An important quote from a source.</blockquote>
                        <p>The article concludes with final thoughts and implications.</p>
                    </article>
                </main>
                <footer>
                    <p>Copyright 2024</p>
                    <nav>Footer links</nav>
                </footer>
            </body>
            </html>
            """;

        stubFor(get(urlEqualTo("/complex-article"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=utf-8")
                        .withBody(complexHtml)));

        String url = wireMockServer.baseUrl() + "/complex-article";
        Optional<String> result = fetcher.fetch(url);

        assertThat(result).isPresent();
        String content = result.get();
        assertThat(content).contains("Breaking News Story");
        assertThat(content).contains("main story begins here");
        // Should not contain navigation/ads
        assertThat(content).doesNotContain("Advertisement");
    }
}
