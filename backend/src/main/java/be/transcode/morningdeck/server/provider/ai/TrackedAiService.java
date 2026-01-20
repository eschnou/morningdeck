package be.transcode.morningdeck.server.provider.ai;

import be.transcode.morningdeck.server.core.exception.InsufficientCreditsException;
import be.transcode.morningdeck.server.core.service.ApiUsageLogService;
import be.transcode.morningdeck.server.core.service.SubscriptionService;
import be.transcode.morningdeck.server.provider.ai.model.EnrichmentResult;
import be.transcode.morningdeck.server.provider.ai.model.EnrichmentWithScoreResult;
import be.transcode.morningdeck.server.provider.ai.model.ExtractedNewsItem;
import be.transcode.morningdeck.server.provider.ai.model.ExtractedWebItem;
import be.transcode.morningdeck.server.provider.ai.model.ReportEmailContent;
import be.transcode.morningdeck.server.provider.ai.model.ScoreResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Decorator that wraps AiService to track usage metrics.
 * - Captures timing (duration_ms)
 * - Extracts token usage from ChatResponse
 * - Logs to ApiUsageLog asynchronously
 *
 * Only active when using the real OpenAI provider (not mock).
 * Marked as @Primary so it's injected by default instead of SpringAiService.
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(prefix = "application.ai", name = "provider", havingValue = "openai")
@RequiredArgsConstructor
public class TrackedAiService implements AiService {

    private final SpringAiService delegate;
    private final ApiUsageLogService usageLogService;
    private final SubscriptionService subscriptionService;

    @Override
    public EnrichmentResult enrich(String title, String content) {
        return trackCall(AiFeature.ENRICH, () -> delegate.enrichTracked(title, content));
    }

    @Override
    public ScoreResult score(String title, String summary, String briefingCriteria) {
        return trackCall(AiFeature.SCORE, () -> delegate.scoreTracked(title, summary, briefingCriteria));
    }

    @Override
    public EnrichmentWithScoreResult enrichWithScore(String title, String content, String briefingCriteria) {
        return trackCall(AiFeature.ENRICH_SCORE,
                () -> delegate.enrichWithScoreTracked(title, content, null, briefingCriteria));
    }

    @Override
    public EnrichmentWithScoreResult enrichWithScore(String title, String content, String webContent, String briefingCriteria) {
        return trackCall(AiFeature.ENRICH_SCORE,
                () -> delegate.enrichWithScoreTracked(title, content, webContent, briefingCriteria));
    }

    @Override
    public List<ExtractedNewsItem> extractFromEmail(String subject, String content) {
        return trackCall(AiFeature.EMAIL_EXTRACT, () -> delegate.extractFromEmailTracked(subject, content));
    }

    @Override
    public List<ExtractedWebItem> extractFromWeb(String pageContent, String extractionPrompt) {
        return trackCall(AiFeature.WEB_EXTRACT, () -> delegate.extractFromWebTracked(pageContent, extractionPrompt));
    }

    @Override
    public ReportEmailContent generateReportEmailContent(String briefingName, String briefingDescription, String items) {
        return trackCall(AiFeature.REPORT_GEN,
                () -> delegate.generateReportEmailContentTracked(briefingName, briefingDescription, items));
    }

    /**
     * Wraps an AI call with timing and usage tracking.
     * Includes safety net credit check as defense-in-depth.
     */
    private <T> T trackCall(AiFeature feature, Supplier<AiCallResult<T>> call) {
        UUID userId = AiUsageContext.getUserId();

        // Safety net: verify credits if user context is set
        // This should not normally trigger if upstream checks are working correctly
        if (userId != null) {
            int balance = subscriptionService.getCreditsBalance(userId);
            if (balance <= 0) {
                log.error("SAFETY NET: AI call attempted with zero credits: userId={} feature={}", userId, feature);
                throw new InsufficientCreditsException(userId, 1, balance);
            }
        }

        long startTime = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;
        Usage usage = null;
        String model = null;

        try {
            AiCallResult<T> result = call.get();
            usage = result.usage();
            model = result.model();

            if (usage == null) {
                log.warn("No usage metadata available for AI call: feature={}", feature);
            }

            return result.result();
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            throw e;
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            usageLogService.logAsync(userId, feature, model, usage, success, errorMessage, durationMs);
        }
    }
}
