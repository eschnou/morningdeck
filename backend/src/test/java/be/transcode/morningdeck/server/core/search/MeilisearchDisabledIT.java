package be.transcode.morningdeck.server.core.search;

import be.transcode.morningdeck.server.config.TestConfig;
import be.transcode.morningdeck.server.config.TestcontainersConfiguration;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying app starts correctly when Meilisearch is disabled.
 * This is important for self-hosted deployments that don't want instant search.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@ContextConfiguration(initializers = TestcontainersConfiguration.class)
class MeilisearchDisabledIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldStartWithoutMeilisearchComponents() {
        // Verify the application context loaded successfully
        assertThat(applicationContext).isNotNull();

        // Verify Meilisearch beans are NOT loaded when disabled
        assertThat(applicationContext.getBeanNamesForType(Client.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(Index.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(MeilisearchSearchService.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(MeilisearchSyncService.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(MeilisearchIndexService.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(MeilisearchHealthIndicator.class)).isEmpty();
    }

    @Test
    void shouldNotHaveMeilisearchEnabled() {
        // Verify meilisearch.enabled property is false or not set
        String enabled = applicationContext.getEnvironment().getProperty("meilisearch.enabled");
        assertThat(enabled).satisfiesAnyOf(
                val -> assertThat(val).isNull(),
                val -> assertThat(val).isEqualTo("false")
        );
    }
}
