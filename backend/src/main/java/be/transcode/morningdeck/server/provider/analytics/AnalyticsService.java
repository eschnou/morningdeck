package be.transcode.morningdeck.server.provider.analytics;

import java.util.Map;

public interface AnalyticsService {

    void logEvent(String eventKey, String userId);

    void logEvent(String eventKey, String userId, Map<String, String> properties);
}
