package be.transcode.morningdeck.server.provider.ai;

import java.util.UUID;

/**
 * ThreadLocal context for tracking which user triggered an AI call.
 * Must be set by callers before invoking AiService methods and cleared after.
 *
 * Usage:
 * <pre>
 * try {
 *     AiUsageContext.setUserId(userId);
 *     aiService.enrich(...);
 * } finally {
 *     AiUsageContext.clear();
 * }
 * </pre>
 */
public final class AiUsageContext {

    private static final ThreadLocal<UUID> currentUserId = new ThreadLocal<>();

    private AiUsageContext() {
        // Utility class
    }

    public static void setUserId(UUID userId) {
        currentUserId.set(userId);
    }

    public static UUID getUserId() {
        return currentUserId.get();
    }

    public static void clear() {
        currentUserId.remove();
    }
}
