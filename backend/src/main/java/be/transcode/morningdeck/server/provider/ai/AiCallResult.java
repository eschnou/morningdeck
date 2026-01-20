package be.transcode.morningdeck.server.provider.ai;

import org.springframework.ai.chat.metadata.Usage;

/**
 * Internal result wrapper that carries both the parsed entity and token usage metadata.
 *
 * @param result The parsed result entity
 * @param usage Token usage metadata from the AI response
 * @param model The model identifier used for the call
 */
public record AiCallResult<T>(
    T result,
    Usage usage,
    String model
) {}
