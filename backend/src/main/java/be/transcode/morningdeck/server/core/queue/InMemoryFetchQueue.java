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
 * In-memory implementation of FetchQueue using BlockingQueue and ThreadPoolExecutor.
 * Can be replaced with SQS implementation by using a different Spring profile.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "application.jobs.feed-ingestion", name = "enabled", havingValue = "true")
public class InMemoryFetchQueue implements FetchQueue {

    private final BlockingQueue<UUID> queue;
    private final ExecutorService executor;
    private final FetchWorker fetchWorker;
    private final int workerCount;
    private volatile boolean running = true;

    public InMemoryFetchQueue(
            FetchWorker fetchWorker,
            @Value("${application.jobs.feed-ingestion.queue-capacity:1000}") int capacity,
            @Value("${application.jobs.feed-ingestion.worker-count:4}") int workerCount) {
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.fetchWorker = fetchWorker;
        this.workerCount = workerCount;
        this.executor = Executors.newFixedThreadPool(workerCount);

        log.info("Initializing InMemoryFetchQueue with capacity={}, workers={}", capacity, workerCount);
        startWorkers();
    }

    private void startWorkers() {
        for (int i = 0; i < workerCount; i++) {
            final int workerId = i;
            executor.submit(() -> workerLoop(workerId));
        }
        log.info("Started {} fetch workers", workerCount);
    }

    private void workerLoop(int workerId) {
        log.debug("Fetch worker {} started", workerId);

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                UUID sourceId = queue.poll(1, TimeUnit.SECONDS);
                if (sourceId != null) {
                    log.debug("Worker {} processing source_id={}", workerId, sourceId);
                    try {
                        fetchWorker.process(sourceId);
                    } catch (Exception e) {
                        log.error("Worker {} failed to process source_id={}: {}",
                                workerId, sourceId, e.getMessage(), e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("Fetch worker {} interrupted", workerId);
                break;
            }
        }

        log.debug("Fetch worker {} stopped", workerId);
    }

    @Override
    public boolean enqueue(UUID sourceId) {
        boolean success = queue.offer(sourceId);
        if (!success) {
            log.warn("Queue full, failed to enqueue source_id={}", sourceId);
        } else {
            log.debug("Enqueued source_id={}, queue_size={}", sourceId, queue.size());
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
        log.info("Shutting down InMemoryFetchQueue...");
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

        log.info("InMemoryFetchQueue shutdown complete, {} items remaining in queue", queue.size());
    }
}
