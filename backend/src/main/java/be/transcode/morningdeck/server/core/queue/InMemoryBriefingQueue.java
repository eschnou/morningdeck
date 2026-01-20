package be.transcode.morningdeck.server.core.queue;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * In-memory implementation of BriefingQueue using BlockingQueue and ThreadPoolExecutor.
 * Can be replaced with SQS implementation by using a different Spring profile.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "application.jobs.briefing-execution", name = "enabled", havingValue = "true")
public class InMemoryBriefingQueue implements BriefingQueue {

    private final BlockingQueue<UUID> queue;
    private final ExecutorService executor;
    private final BriefingWorker briefingWorker;
    private final int workerCount;
    private volatile boolean running = true;

    public InMemoryBriefingQueue(
            BriefingWorker briefingWorker,
            @Value("${application.jobs.briefing-execution.queue-capacity:100}") int capacity,
            @Value("${application.jobs.briefing-execution.worker-count:1}") int workerCount) {
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.briefingWorker = briefingWorker;
        this.workerCount = workerCount;
        this.executor = Executors.newFixedThreadPool(workerCount);

        log.info("Initializing InMemoryBriefingQueue with capacity={}, workers={}", capacity, workerCount);
        startWorkers();
    }

    private void startWorkers() {
        for (int i = 0; i < workerCount; i++) {
            final int workerId = i;
            executor.submit(() -> workerLoop(workerId));
        }
        log.info("Started {} briefing workers", workerCount);
    }

    private void workerLoop(int workerId) {
        log.debug("Briefing worker {} started", workerId);

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                UUID dayBriefId = queue.poll(1, TimeUnit.SECONDS);
                if (dayBriefId != null) {
                    log.debug("Worker {} processing day_brief_id={}", workerId, dayBriefId);
                    try {
                        briefingWorker.process(dayBriefId);
                    } catch (Exception e) {
                        log.error("Worker {} failed to process day_brief_id={}: {}",
                                workerId, dayBriefId, e.getMessage(), e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("Briefing worker {} interrupted", workerId);
                break;
            }
        }

        log.debug("Briefing worker {} stopped", workerId);
    }

    @Override
    public boolean enqueue(UUID dayBriefId) {
        boolean success = queue.offer(dayBriefId);
        if (!success) {
            log.warn("Queue full, failed to enqueue day_brief_id={}", dayBriefId);
        } else {
            log.debug("Enqueued day_brief_id={}, queue_size={}", dayBriefId, queue.size());
        }
        return success;
    }

    @Override
    public boolean canAccept() {
        return queue.remainingCapacity() > 0;
    }

    @Override
    public int size() {
        return queue.size();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down InMemoryBriefingQueue...");
        running = false;
        executor.shutdown();

        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Shutdown interrupted, forcing shutdown");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("InMemoryBriefingQueue shutdown complete, {} items remaining in queue", queue.size());
    }
}
