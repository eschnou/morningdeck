package be.transcode.morningdeck.server.core.model;

public enum FetchStatus {
    IDLE,       // Ready to be scheduled
    QUEUED,     // In queue, waiting for worker
    FETCHING    // Worker is actively fetching
}
