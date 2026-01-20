package be.transcode.morningdeck.server.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class CoreEngineMetrics {

    private final MeterRegistry registry;

    public void recordFeedFetch(UUID sourceId, boolean success, long durationMs) {
        Counter.builder("feed.fetch")
                .tag("source_id", sourceId.toString())
                .tag("success", String.valueOf(success))
                .register(registry)
                .increment();

        Timer.builder("feed.fetch.duration")
                .tag("success", String.valueOf(success))
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordNewsProcessing(String stage, boolean success) {
        Counter.builder("news.processing")
                .tag("stage", stage)
                .tag("success", String.valueOf(success))
                .register(registry)
                .increment();
    }

    public void recordBriefingExecution(UUID dayBriefId, int itemCount) {
        Counter.builder("briefing.execution")
                .tag("day_brief_id", dayBriefId.toString())
                .register(registry)
                .increment();

        registry.gauge("briefing.items.last", itemCount);
    }

    public void recordSourceError(UUID sourceId, String errorType) {
        Counter.builder("source.error")
                .tag("source_id", sourceId.toString())
                .tag("error_type", errorType)
                .register(registry)
                .increment();
    }
}
