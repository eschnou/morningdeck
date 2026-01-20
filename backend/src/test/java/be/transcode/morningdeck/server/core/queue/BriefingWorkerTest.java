package be.transcode.morningdeck.server.core.queue;

import be.transcode.morningdeck.server.core.model.*;
import be.transcode.morningdeck.server.core.repository.DailyReportRepository;
import be.transcode.morningdeck.server.core.repository.DayBriefRepository;
import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import be.transcode.morningdeck.server.core.service.ReportEmailDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BriefingWorker Unit Tests")
@ExtendWith(MockitoExtension.class)
class BriefingWorkerTest {

    @Mock
    private DayBriefRepository dayBriefRepository;

    @Mock
    private NewsItemRepository newsItemRepository;

    @Mock
    private DailyReportRepository dailyReportRepository;

    @Mock
    private ReportEmailDeliveryService reportEmailDeliveryService;

    private BriefingWorker worker;

    @BeforeEach
    void setUp() {
        worker = new BriefingWorker(dayBriefRepository, newsItemRepository, dailyReportRepository, reportEmailDeliveryService);
    }

    private DayBrief createBriefWithSource(UUID briefId, BriefingFrequency frequency, Instant lastExecutedAt) {
        DayBrief brief = DayBrief.builder()
                .id(briefId)
                .userId(UUID.randomUUID())
                .title("Test Brief")
                .briefing("Test")
                .frequency(frequency)
                .scheduleTime(LocalTime.of(8, 0))
                .timezone("UTC")
                .status(DayBriefStatus.QUEUED)
                .lastExecutedAt(lastExecutedAt)
                .sources(new ArrayList<>())
                .position(0)
                .build();

        // Add a source to the brief
        Source source = Source.builder()
                .id(UUID.randomUUID())
                .dayBrief(brief)
                .name("Test Source")
                .url("http://example.com/feed")
                .type(SourceType.RSS)
                .status(SourceStatus.ACTIVE)
                .build();
        brief.getSources().add(source);

        return brief;
    }

    @Nested
    @DisplayName("Lookback period for first execution")
    class LookbackPeriodTests {

        @Test
        @DisplayName("DAILY brief with no lastExecutedAt should look back 1 day")
        void dailyBriefShouldLookBack1Day() {
            // Given: A DAILY brief with no previous execution
            UUID briefId = UUID.randomUUID();
            DayBrief brief = createBriefWithSource(briefId, BriefingFrequency.DAILY, null);

            when(dayBriefRepository.findByIdWithSources(briefId)).thenReturn(Optional.of(brief));
            when(newsItemRepository.findTopScoredItems(any(), eq(NewsItemStatus.DONE), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(dailyReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(dayBriefRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            worker.process(briefId);

            // Then: Verify the "since" parameter is approximately 1 day ago
            ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(newsItemRepository).findTopScoredItems(any(), eq(NewsItemStatus.DONE), sinceCaptor.capture(), any(Pageable.class));

            Instant since = sinceCaptor.getValue();
            Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
            // Allow 1 minute tolerance for test execution time
            assertThat(since).isBetween(oneDayAgo.minus(1, ChronoUnit.MINUTES), oneDayAgo.plus(1, ChronoUnit.MINUTES));
        }

        @Test
        @DisplayName("WEEKLY brief with no lastExecutedAt should look back 7 days")
        void weeklyBriefShouldLookBack7Days() {
            // Given: A WEEKLY brief with no previous execution
            UUID briefId = UUID.randomUUID();
            DayBrief brief = createBriefWithSource(briefId, BriefingFrequency.WEEKLY, null);

            when(dayBriefRepository.findByIdWithSources(briefId)).thenReturn(Optional.of(brief));
            when(newsItemRepository.findTopScoredItems(any(), eq(NewsItemStatus.DONE), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(dailyReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(dayBriefRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            worker.process(briefId);

            // Then: Verify the "since" parameter is approximately 7 days ago
            ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(newsItemRepository).findTopScoredItems(any(), eq(NewsItemStatus.DONE), sinceCaptor.capture(), any(Pageable.class));

            Instant since = sinceCaptor.getValue();
            Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
            // Allow 1 minute tolerance for test execution time
            assertThat(since).isBetween(sevenDaysAgo.minus(1, ChronoUnit.MINUTES), sevenDaysAgo.plus(1, ChronoUnit.MINUTES));
        }

        @Test
        @DisplayName("Brief with lastExecutedAt should use that timestamp regardless of frequency")
        void briefWithLastExecutedAtShouldUseThatTimestamp() {
            // Given: A brief that was last executed 3 days ago
            UUID briefId = UUID.randomUUID();
            Instant lastExecuted = Instant.now().minus(3, ChronoUnit.DAYS);
            DayBrief brief = createBriefWithSource(briefId, BriefingFrequency.WEEKLY, lastExecuted);

            when(dayBriefRepository.findByIdWithSources(briefId)).thenReturn(Optional.of(brief));
            when(newsItemRepository.findTopScoredItems(any(), eq(NewsItemStatus.DONE), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(dailyReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(dayBriefRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            worker.process(briefId);

            // Then: Verify the "since" parameter is the lastExecutedAt timestamp
            ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(newsItemRepository).findTopScoredItems(any(), eq(NewsItemStatus.DONE), sinceCaptor.capture(), any(Pageable.class));

            Instant since = sinceCaptor.getValue();
            assertThat(since).isEqualTo(lastExecuted);
        }
    }
}
