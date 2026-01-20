package be.transcode.morningdeck.server.core.queue;

import java.util.UUID;

/**
 * Interface for the feed fetch queue.
 * Abstracts queue operations to enable swapping implementations (in-memory vs SQS).
 */
public interface FetchQueue {

    /**
     * Add a source to the fetch queue.
     *
     * @param sourceId The source ID to fetch
     * @return true if successfully enqueued, false if queue is full
     */
    boolean enqueue(UUID sourceId);

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
