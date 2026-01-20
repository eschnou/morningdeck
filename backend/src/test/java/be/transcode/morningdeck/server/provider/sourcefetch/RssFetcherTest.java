package be.transcode.morningdeck.server.provider.sourcefetch;

import be.transcode.morningdeck.server.core.model.Source;
import be.transcode.morningdeck.server.core.model.SourceType;
import be.transcode.morningdeck.server.core.util.HtmlToMarkdownConverter;
import be.transcode.morningdeck.server.provider.sourcefetch.model.FetchedItem;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class RssFetcherTest {

    private RssFetcher rssFetcher;

    @BeforeEach
    void setUp() {
        rssFetcher = new RssFetcher(new HtmlToMarkdownConverter());
    }

    @Test
    void shouldReturnRssSourceType() {
        assertThat(rssFetcher.getSourceType()).isEqualTo(SourceType.RSS);
    }

    @Test
    void shouldParseStandardRssFeedWithPubDate(WireMockRuntimeInfo wmRuntimeInfo) {
        String rssFeed = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                    <channel>
                        <title>Test Blog</title>
                        <link>https://example.com</link>
                        <description>A test blog</description>
                        <item>
                            <title>Test Article</title>
                            <link>https://example.com/article</link>
                            <pubDate>Mon, 13 Jan 2025 10:30:00 GMT</pubDate>
                            <description>Article content here</description>
                        </item>
                    </channel>
                </rss>
                """;

        stubFor(get("/rss.xml")
                .willReturn(ok(rssFeed)
                        .withHeader("Content-Type", "application/rss+xml")));

        Source source = new Source();
        source.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/rss.xml");

        List<FetchedItem> items = rssFetcher.fetch(source, null);

        assertThat(items).hasSize(1);
        FetchedItem item = items.get(0);
        assertThat(item.getTitle()).isEqualTo("Test Article");
        assertThat(item.getPublishedAt()).isNotNull();
    }

    @Test
    void shouldParseAtom03FeedWithIssuedDate(WireMockRuntimeInfo wmRuntimeInfo) {
        // Atom 0.3 format with proper namespace
        String atomFeed = """
                <?xml version="1.0" encoding="utf-8"?>
                <feed version="0.3" xmlns="http://purl.org/atom/ns#">
                    <title>Test Blog</title>
                    <link type="text/html" rel="alternate" href="https://example.com/"/>
                    <entry>
                        <title>Writing Blank Verse</title>
                        <link type="text/html" rel="alternate" href="https://example.com/journal/2025.html"/>
                        <id>tag:example.com,2025:entry1</id>
                        <issued>2026-01-13</issued>
                        <modified>2026-01-13</modified>
                        <summary>Test summary</summary>
                    </entry>
                </feed>
                """;

        stubFor(get("/atom03.xml")
                .willReturn(ok(atomFeed)
                        .withHeader("Content-Type", "application/atom+xml")));

        Source source = new Source();
        source.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/atom03.xml");

        List<FetchedItem> items = rssFetcher.fetch(source, null);

        assertThat(items).hasSize(1);
        FetchedItem item = items.get(0);
        assertThat(item.getTitle()).isEqualTo("Writing Blank Verse");
        assertThat(item.getPublishedAt())
                .isEqualTo(LocalDate.of(2026, 1, 13).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    @Test
    void shouldFallbackToCurrentTimestampWhenDateCannotBeParsed(WireMockRuntimeInfo wmRuntimeInfo) {
        // Malformed feed: Atom 1.0 namespace but Atom 0.3 date tags (Rome can't parse these)
        String malformedFeed = """
                <?xml version="1.0"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                    <title>Test Blog</title>
                    <link rel="alternate" type="text/html" href="https://example.com/"/>
                    <entry>
                        <title>Entry with unparseable date</title>
                        <link type="text/html" rel="alternate" href="https://example.com/entry"/>
                        <id>tag:example.com,2025:entry1</id>
                        <issued>2026-01-13</issued>
                        <modified>2026-01-13</modified>
                        <summary>Test summary</summary>
                    </entry>
                </feed>
                """;

        stubFor(get("/malformed.xml")
                .willReturn(ok(malformedFeed)
                        .withHeader("Content-Type", "application/atom+xml")));

        Source source = new Source();
        source.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/malformed.xml");

        Instant beforeFetch = Instant.now();
        List<FetchedItem> items = rssFetcher.fetch(source, null);
        Instant afterFetch = Instant.now();

        assertThat(items).hasSize(1);
        FetchedItem item = items.get(0);
        assertThat(item.getTitle()).isEqualTo("Entry with unparseable date");
        // Should fallback to current timestamp
        assertThat(item.getPublishedAt()).isNotNull();
        assertThat(item.getPublishedAt()).isBetween(beforeFetch.minusSeconds(1), afterFetch.plusSeconds(1));
    }
}
