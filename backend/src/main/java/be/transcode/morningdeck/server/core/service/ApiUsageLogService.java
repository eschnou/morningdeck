package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.core.model.ApiUsageLog;
import be.transcode.morningdeck.server.core.model.User;
import be.transcode.morningdeck.server.core.repository.ApiUsageLogRepository;
import be.transcode.morningdeck.server.core.repository.UserRepository;
import be.transcode.morningdeck.server.provider.ai.AiFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for logging API usage asynchronously.
 * Logging failures are swallowed to avoid affecting the main flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiUsageLogService {

    private final ApiUsageLogRepository repository;
    private final UserRepository userRepository;

    /**
     * Log API usage asynchronously.
     *
     * @param userId User who triggered the call (may be null)
     * @param feature The AI feature being used
     * @param model The model identifier
     * @param usage Token usage metadata (may be null)
     * @param success Whether the call succeeded
     * @param errorMessage Error message if failed (may be null)
     * @param durationMs Call duration in milliseconds
     */
    @Async
    public void logAsync(UUID userId, AiFeature feature, String model,
                         Usage usage, boolean success, String errorMessage, long durationMs) {
        try {
            User user = userId != null ? userRepository.findById(userId).orElse(null) : null;

            if (userId != null && user == null) {
                log.warn("User not found for API usage log: userId={}", userId);
            }

            ApiUsageLog usageLog = ApiUsageLog.builder()
                    .user(user)
                    .featureKey(feature)
                    .model(model)
                    .inputTokens(usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : null)
                    .outputTokens(usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : null)
                    .totalTokens(usage != null && usage.getTotalTokens() != null ? usage.getTotalTokens().longValue() : null)
                    .success(success)
                    .errorMessage(truncate(errorMessage, 1024))
                    .durationMs(durationMs)
                    .build();

            repository.save(usageLog);

            log.info("API usage logged: feature={} userId={} tokens={} durationMs={} success={}",
                    feature, userId, usage != null ? usage.getTotalTokens() : null, durationMs, success);

        } catch (Exception e) {
            // Swallow - logging failure must not affect main flow
            log.error("Failed to log API usage: feature={} userId={} error={}",
                    feature, userId, e.getMessage());
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
