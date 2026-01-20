package be.transcode.morningdeck.server.core.model;

public enum NewsItemStatus {
    NEW,        // Just arrived, waiting to be scheduled for processing
    PENDING,    // Queued for processing
    PROCESSING, // Processing in progress
    DONE,       // Fully processed
    ERROR       // Failed after max retries
}
