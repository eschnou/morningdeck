package be.transcode.morningdeck.server.provider.webfetch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NoOpWebFetcher Unit Tests")
class NoOpWebFetcherTest {

    private NoOpWebFetcher noOpWebFetcher;

    @BeforeEach
    void setUp() {
        noOpWebFetcher = new NoOpWebFetcher();
    }

    @Test
    @DisplayName("Should return empty for any URL")
    void shouldReturnEmptyForAnyUrl() {
        Optional<String> result = noOpWebFetcher.fetch("https://example.com/article");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty for null URL")
    void shouldReturnEmptyForNullUrl() {
        Optional<String> result = noOpWebFetcher.fetch(null);

        assertThat(result).isEmpty();
    }
}
