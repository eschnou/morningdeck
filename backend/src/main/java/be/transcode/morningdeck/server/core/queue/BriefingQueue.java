package be.transcode.morningdeck.server.core.queue;

import java.util.UUID;

/**
 * Interface for the briefing execution queue.
 * Abstracts queue operations to enable swapping implementations (in-memory vs SQS).
 */
public interface BriefingQueue {

    /**
     * Add a day brief to the execution queue.
     *
     * @param dayBriefId The DayBrief ID to process
     * @return true if successfully enqueued, false if queue is full
     */
    boolean enqueue(UUID dayBriefId);

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
