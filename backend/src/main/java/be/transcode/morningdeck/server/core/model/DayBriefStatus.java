package be.transcode.morningdeck.server.core.model;

public enum DayBriefStatus {
    ACTIVE,     // Enabled & idle (ready to be scheduled)
    QUEUED,     // In queue, waiting for worker
    PROCESSING, // Worker is executing this briefing
    ERROR,      // Failed processing
    PAUSED      // Disabled by user
}
