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
 * In-memory implementation of ProcessingQueue using BlockingQueue and ThreadPoolExecutor.
 * Can be replaced with SQS implementation by using a different Spring profile.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "application.jobs.news-processing", name = "enabled", havingValue = "true")
public class InMemoryProcessingQueue implements ProcessingQueue {

    private final BlockingQueue<UUID> queue;
    private final ExecutorService executor;
    private final ProcessingWorker processingWorker;
    private final int workerCount;
    private volatile boolean running = true;

    public InMemoryProcessingQueue(
            ProcessingWorker processingWorker,
            @Value("${application.jobs.news-processing.queue-capacity:500}") int capacity,
            @Value("${application.jobs.news-processing.worker-count:2}") int workerCount) {
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.processingWorker = processingWorker;
        this.workerCount = workerCount;
        this.executor = Executors.newFixedThreadPool(workerCount);

        log.info("Initializing InMemoryProcessingQueue with capacity={}, workers={}", capacity, workerCount);
        startWorkers();
    }

    private void startWorkers() {
        for (int i = 0; i < workerCount; i++) {
            final int workerId = i;
            executor.submit(() -> workerLoop(workerId));
        }
        log.info("Started {} processing workers", workerCount);
    }

    private void workerLoop(int workerId) {
        log.debug("Processing worker {} started", workerId);

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                UUID newsItemId = queue.poll(1, TimeUnit.SECONDS);
                if (newsItemId != null) {
                    log.debug("Worker {} processing news_item_id={}", workerId, newsItemId);
                    try {
                        processingWorker.process(newsItemId);
                    } catch (Exception e) {
                        log.error("Worker {} failed to process news_item_id={}: {}",
                                workerId, newsItemId, e.getMessage(), e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("Processing worker {} interrupted", workerId);
                break;
            }
        }

        log.debug("Processing worker {} stopped", workerId);
    }

    @Override
    public boolean enqueue(UUID newsItemId) {
        boolean success = queue.offer(newsItemId);
        if (!success) {
            log.warn("Queue full, failed to enqueue news_item_id={}", newsItemId);
        } else {
            log.debug("Enqueued news_item_id={}, queue_size={}", newsItemId, queue.size());
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
        log.info("Shutting down InMemoryProcessingQueue...");
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

        log.info("InMemoryProcessingQueue shutdown complete, {} items remaining in queue", queue.size());
    }
}
