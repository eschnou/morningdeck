package be.transcode.morningdeck.server.core.job;

import be.transcode.morningdeck.server.core.model.BriefingFrequency;
import be.transcode.morningdeck.server.core.model.DayBrief;
import be.transcode.morningdeck.server.core.queue.BriefingQueue;
import be.transcode.morningdeck.server.core.repository.DayBriefRepository;
import be.transcode.morningdeck.server.core.service.DayBriefService;
import be.transcode.morningdeck.server.core.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BriefingSchedulerJob Unit Tests")
@ExtendWith(MockitoExtension.class)
class BriefingSchedulerJobTest {

    @Mock
    private DayBriefRepository dayBriefRepository;

    @Mock
    private DayBriefService dayBriefService;

    @Mock
    private BriefingQueue briefingQueue;

    @Mock
    private SubscriptionService subscriptionService;

    private BriefingSchedulerJob schedulerJob;

    @BeforeEach
    void setUp() {
        schedulerJob = new BriefingSchedulerJob(dayBriefRepository, dayBriefService, briefingQueue, subscriptionService);
    }

    @Nested
    @DisplayName("isBriefingDue timezone handling")
    class IsBriefingDueTests {

        @Test
        @DisplayName("should trigger briefing when schedule time has passed in user's timezone")
        void shouldTriggerWhenScheduleTimePassed() {
            // Given: It's 10:00 UTC, user is in Europe/Paris (UTC+1), so it's 11:00 local
            // Briefing scheduled for 09:00 Paris time should be due
            Instant nowUtc = ZonedDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant();
            DayBrief briefing = DayBrief.builder()
                    .id(UUID.randomUUID())
                    .scheduleTime(LocalTime.of(9, 0))
                    .timezone("Europe/Paris")
                    .build();

            // When
            boolean isDue = schedulerJob.isBriefingDue(briefing, nowUtc);

            // Then
            assertThat(isDue).isTrue();
        }

        @Test
        @DisplayName("should not trigger briefing when schedule time has not passed in user's timezone")
        void shouldNotTriggerWhenScheduleTimeNotPassed() {
            // Given: It's 07:00 UTC, user is in Europe/Paris (UTC+1), so it's 08:00 local
            // Briefing scheduled for 09:00 Paris time should NOT be due
            Instant nowUtc = ZonedDateTime.of(2024, 1, 15, 7, 0, 0, 0, ZoneId.of("UTC")).toInstant();
            DayBrief briefing = DayBrief.builder()
                    .id(UUID.randomUUID())
                    .scheduleTime(LocalTime.of(9, 0))
                    .timezone("Europe/Paris")
                    .build();

            // When
            boolean isDue = schedulerJob.isBriefingDue(briefing, nowUtc);

            // Then
            assertThat(isDue).isFalse();
        }

        @Test
        @DisplayName("should trigger briefing exactly at schedule time")
        void shouldTriggerExactlyAtScheduleTime() {
            // Given: It's 08:00 UTC, user is in Europe/Paris (UTC+1), so it's 09:00 local
            // Briefing scheduled for 09:00 Paris time should be due
            Instant nowUtc = ZonedDateTime.of(2024, 1, 15, 8, 0, 0, 0, ZoneId.of("UTC")).toInstant();
            DayBrief briefing = DayBrief.builder()
                    .id(UUID.randomUUID())
                    .scheduleTime(LocalTime.of(9, 0))
                    .timezone("Europe/Paris")
                    .build();

            // When
            boolean isDue = schedulerJob.isBriefingDue(briefing, nowUtc);

            // Then
            assertThat(isDue).isTrue();
        }

        @Test
        @DisplayName("should handle negative UTC offset timezone correctly")
        void shouldHandleNegativeOffsetTimezone() {
            // Given: It's 15:00 UTC, user is in America/New_York (UTC-5), so it's 10:00 local
            // Briefing scheduled for 09:00 New York time should be due
            Instant nowUtc = ZonedDateTime.of(2024, 1, 15, 15, 0, 0, 0, ZoneId.of("UTC")).toInstant();
            DayBrief briefing = DayBrief.builder()
                    .id(UUID.randomUUID())
                    .scheduleTime(LocalTime.of(9, 0))
                    .timezone("America/New_York")
                    .build();

            // When
            boolean isDue = schedulerJob.isBriefingDue(briefing, nowUtc);

            // Then
            assertThat(isDue).isTrue();
        }

        @Test
        @DisplayName("should not trigger briefing in negative offset timezone before schedule time")
        void shouldNotTriggerNegativeOffsetTimezoneBeforeScheduleTime() {
            // Given: It's 13:00 UTC, user is in America/New_York (UTC-5), so it's 08:00 local
            // Briefing scheduled for 09:00 New York time should NOT be due
            Instant nowUtc = ZonedDateTime.of(2024, 1, 15, 13, 0, 0, 0, ZoneId.of("UTC")).toInstant();
            DayBrief briefing = DayBrief.builder()
                    .id(UUID.randomUUID())
                    .scheduleTime(LocalTime.of(9, 0))
                    .timezone("America/New_York")
                    .build();

            // When
            boolean isDue = schedulerJob.isBriefingDue(briefing, nowUtc);

            // Then
            assertThat(isDue).isFalse();
        }

        @Test
        @DisplayName("should handle UTC timezone correctly")
        void shouldHandleUtcTimezone() {
            // Given: It's 09:30 UTC, briefing scheduled for 09:00 UTC
            Instant nowUtc = ZonedDateTime.of(2024, 1, 15, 9, 30, 0, 0, ZoneId.of("UTC")).toInstant();
            DayBrief briefing = DayBrief.builder()
                    .id(UUID.randomUUID())
                    .scheduleTime(LocalTime.of(9, 0))
                    .timezone("UTC")
                    .build();

            // When
            boolean isDue = schedulerJob.isBriefingDue(briefing, nowUtc);

            // Then
            assertThat(isDue).isTrue();
        }

        @Test
        @DisplayName("should fall back to UTC for invalid timezone")
        void shouldFallBackToUtcForInvalidTimezone() {
            // Given: Invalid timezone, should use UTC
            Instant nowUtc = ZonedDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant();
            DayBrief briefing = DayBrief.builder()
                    .id(UUID.randomUUID())
                    .scheduleTime(LocalTime.of(9, 0))
                    .timezone("Invalid/Timezone")
                    .build();

            // When
            boolean isDue = schedulerJob.isBriefingDue(briefing, nowUtc);

            // Then: Should use UTC comparison, 10:00 UTC >= 09:00 UTC, so due
            assertThat(isDue).isTrue();
        }

        @Test
        @DisplayName("should handle day boundary for positive offset timezone")
        void shouldHandleDayBoundaryPositiveOffset() {
            // Given: It's 23:30 UTC on Jan 15, user is in Asia/Tokyo (UTC+9), so it's 08:30 on Jan 16
            // Briefing scheduled for 09:00 Tokyo time should NOT be due (it's still 08:30 local)
            Instant nowUtc = ZonedDateTime.of(2024, 1, 15, 23, 30, 0, 0, ZoneId.of("UTC")).toInstant();
            DayBrief briefing = DayBrief.builder()
                    .id(UUID.randomUUID())
                    .scheduleTime(LocalTime.of(9, 0))
                    .timezone("Asia/Tokyo")
                    .build();

            // When
            boolean isDue = schedulerJob.isBriefingDue(briefing, nowUtc);

            // Then
            assertThat(isDue).isFalse();
        }

        @Test
        @DisplayName("should handle day boundary for negative offset timezone")
        void shouldHandleDayBoundaryNegativeOffset() {
            // Given: It's 03:00 UTC on Jan 16, user is in America/Los_Angeles (UTC-8), so it's 19:00 on Jan 15
            // Briefing scheduled for 18:00 LA time should be due
            Instant nowUtc = ZonedDateTime.of(2024, 1, 16, 3, 0, 0, 0, ZoneId.of("UTC")).toInstant();
            DayBrief briefing = DayBrief.builder()
                    .id(UUID.randomUUID())
                    .scheduleTime(LocalTime.of(18, 0))
                    .timezone("America/Los_Angeles")
                    .build();

            // When
            boolean isDue = schedulerJob.isBriefingDue(briefing, nowUtc);

            // Then
            assertThat(isDue).isTrue();
        }

        @Test
        @DisplayName("WEEKLY brief should trigger on correct day of week")
        void weeklyBriefShouldTriggerOnCorrectDay() {
            // Given: It's Monday 10:00 UTC, briefing scheduled for Monday 09:00 UTC
            // January 15, 2024 is a Monday
            Instant nowUtc = ZonedDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant();
            DayBrief briefing = DayBrief.builder()
                    .id(UUID.randomUUID())
                    .frequency(BriefingFrequency.WEEKLY)
                    .scheduleDayOfWeek(DayOfWeek.MONDAY)
                    .scheduleTime(LocalTime.of(9, 0))
                    .timezone("UTC")
                    .build();

            // When
            boolean isDue = schedulerJob.isBriefingDue(briefing, nowUtc);

            // Then
            assertThat(isDue).isTrue();
        }

        @Test
        @DisplayName("WEEKLY brief should NOT trigger on wrong day of week")
        void weeklyBriefShouldNotTriggerOnWrongDay() {
            // Given: It's Monday 10:00 UTC, but briefing is scheduled for Tuesday
            // January 15, 2024 is a Monday
            Instant nowUtc = ZonedDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant();
            DayBrief briefing = DayBrief.builder()
                    .id(UUID.randomUUID())
                    .frequency(BriefingFrequency.WEEKLY)
                    .scheduleDayOfWeek(DayOfWeek.TUESDAY)
                    .scheduleTime(LocalTime.of(9, 0))
                    .timezone("UTC")
                    .build();

            // When
            boolean isDue = schedulerJob.isBriefingDue(briefing, nowUtc);

            // Then
            assertThat(isDue).isFalse();
        }

        @Test
        @DisplayName("DAILY brief should ignore scheduleDayOfWeek field")
        void dailyBriefShouldIgnoreDayOfWeek() {
            // Given: It's Monday 10:00 UTC, DAILY briefing has scheduleDayOfWeek=TUESDAY (should be ignored)
            // January 15, 2024 is a Monday
            Instant nowUtc = ZonedDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant();
            DayBrief briefing = DayBrief.builder()
                    .id(UUID.randomUUID())
                    .frequency(BriefingFrequency.DAILY)
                    .scheduleDayOfWeek(DayOfWeek.TUESDAY) // Should be ignored for DAILY
                    .scheduleTime(LocalTime.of(9, 0))
                    .timezone("UTC")
                    .build();

            // When
            boolean isDue = schedulerJob.isBriefingDue(briefing, nowUtc);

            // Then: DAILY brief should trigger regardless of day
            assertThat(isDue).isTrue();
        }

        @Test
        @DisplayName("WEEKLY brief with null scheduleDayOfWeek should trigger any day (backward compatibility)")
        void weeklyBriefWithNullDayOfWeekShouldTriggerAnyDay() {
            // Given: WEEKLY briefing without scheduleDayOfWeek set (null)
            Instant nowUtc = ZonedDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant();
            DayBrief briefing = DayBrief.builder()
                    .id(UUID.randomUUID())
                    .frequency(BriefingFrequency.WEEKLY)
                    .scheduleDayOfWeek(null)
                    .scheduleTime(LocalTime.of(9, 0))
                    .timezone("UTC")
                    .build();

            // When
            boolean isDue = schedulerJob.isBriefingDue(briefing, nowUtc);

            // Then: Should trigger for backward compatibility
            assertThat(isDue).isTrue();
        }

        @Test
        @DisplayName("WEEKLY brief should respect timezone when checking day of week")
        void weeklyBriefShouldRespectTimezoneForDayCheck() {
            // Given: It's Sunday 23:00 UTC, but in Asia/Tokyo (UTC+9) it's Monday 08:00
            // Briefing scheduled for Monday 07:00 Tokyo time
            Instant nowUtc = ZonedDateTime.of(2024, 1, 14, 23, 0, 0, 0, ZoneId.of("UTC")).toInstant();
            DayBrief briefing = DayBrief.builder()
                    .id(UUID.randomUUID())
                    .frequency(BriefingFrequency.WEEKLY)
                    .scheduleDayOfWeek(DayOfWeek.MONDAY)
                    .scheduleTime(LocalTime.of(7, 0))
                    .timezone("Asia/Tokyo")
                    .build();

            // When
            boolean isDue = schedulerJob.isBriefingDue(briefing, nowUtc);

            // Then: Should trigger because in Tokyo it's Monday
            assertThat(isDue).isTrue();
        }
    }
}
