package be.transcode.morningdeck.server.core.queue;

import java.util.UUID;

/**
 * Interface for the news item processing queue.
 * Abstracts queue operations to enable swapping implementations (in-memory vs SQS).
 */
public interface ProcessingQueue {

    /**
     * Add a news item to the processing queue.
     *
     * @param newsItemId The news item ID to process
     * @return true if successfully enqueued, false if queue is full
     */
    boolean enqueue(UUID newsItemId);

    /**
     * Check if the queue is accepting new items.
     * Used for backpressure when queue is full.
     */
    boolean canAccept();

    /**
     * Get current queue depth (for monitoring).
     */
    int size();
}
