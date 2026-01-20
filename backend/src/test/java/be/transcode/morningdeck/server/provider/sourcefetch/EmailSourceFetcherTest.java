package be.transcode.morningdeck.server.provider.sourcefetch;

import be.transcode.morningdeck.server.core.model.Source;
import be.transcode.morningdeck.server.core.model.SourceType;
import be.transcode.morningdeck.server.provider.sourcefetch.model.FetchedItem;
import be.transcode.morningdeck.server.provider.sourcefetch.model.SourceValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailSourceFetcher Unit Tests")
class EmailSourceFetcherTest {

    private EmailSourceFetcher emailSourceFetcher;

    @BeforeEach
    void setUp() {
        emailSourceFetcher = new EmailSourceFetcher();
    }

    @Test
    @DisplayName("Should return EMAIL source type")
    void shouldReturnEmailSourceType() {
        assertThat(emailSourceFetcher.getSourceType()).isEqualTo(SourceType.EMAIL);
    }

    @Test
    @DisplayName("Validate should always return success")
    void validateShouldAlwaysReturnSuccess() {
        SourceValidationResult result = emailSourceFetcher.validate("any-value");

        assertThat(result.isValid()).isTrue();
        assertThat(result.getFeedTitle()).isEqualTo("Email Source");
        assertThat(result.getFeedDescription()).isEqualTo("Receives emails at generated address");
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("Validate should succeed with null URL")
    void validateShouldSucceedWithNullUrl() {
        SourceValidationResult result = emailSourceFetcher.validate(null);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Fetch should return empty list (push-based)")
    void fetchShouldReturnEmptyList() {
        Source source = Source.builder()
                .id(UUID.randomUUID())
                .name("Test Email Source")
                .type(SourceType.EMAIL)
                .build();

        List<FetchedItem> items = emailSourceFetcher.fetch(source, Instant.now().minusSeconds(24 * 3600));

        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("Fetch should return empty list with null lastFetchedAt")
    void fetchShouldReturnEmptyListWithNullLastFetched() {
        Source source = Source.builder()
                .id(UUID.randomUUID())
                .name("Test Email Source")
                .type(SourceType.EMAIL)
                .build();

        List<FetchedItem> items = emailSourceFetcher.fetch(source, null);

        assertThat(items).isEmpty();
    }
}
