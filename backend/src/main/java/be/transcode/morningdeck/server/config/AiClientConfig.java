package be.transcode.morningdeck.server.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for AI ChatClients with different model tiers.
 * Creates separate clients for lightweight (LITE) and heavy (HEAVY) tasks.
 */
@Configuration
@ConditionalOnProperty(prefix = "application.ai", name = "provider", havingValue = "openai")
public class AiClientConfig {

    @Bean
    @Qualifier("liteClient")
    public ChatClient liteClient(
            ChatClient.Builder builder,
            @Value("${application.ai.models.lite}") String model) {
        return builder
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                .build();
    }

    @Bean
    @Qualifier("heavyClient")
    public ChatClient heavyClient(
            ChatClient.Builder builder,
            @Value("${application.ai.models.heavy}") String model) {
        return builder
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                .build();
    }
}
