package be.transcode.morningdeck.server.provider.analytics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "analytics", name = "provider", havingValue = "logs", matchIfMissing = true)
public class LogsAnalyticsService implements AnalyticsService {
    @Override
    public void logEvent(String eventKey, String userId) {
        log.info(eventKey);
    }

    @Override
    public void logEvent(String eventKey, String userId, Map<String, String> properties) {
        log.info(eventKey);
    }
}
