package be.transcode.morningdeck.server.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "application")
public class CoreEngineProperties {

    private Jobs jobs = new Jobs();
    private Ai ai = new Ai();

    @Data
    public static class Jobs {
        private FeedIngestion feedIngestion = new FeedIngestion();
        private FeedScheduling feedScheduling = new FeedScheduling();
        private FeedRecovery feedRecovery = new FeedRecovery();
        private NewsProcessing newsProcessing = new NewsProcessing();
        private BriefingExecution briefingExecution = new BriefingExecution();

        @Data
        public static class FeedIngestion {
            private boolean enabled = true;
            private int queueCapacity = 1000;
            private int workerCount = 4;
            private int batchSize = 100;
            private int stuckThresholdMinutes = 10;
        }

        @Data
        public static class FeedScheduling {
            private long interval = 60000; // 1 minute
        }

        @Data
        public static class FeedRecovery {
            private long interval = 300000; // 5 minutes
        }

        @Data
        public static class NewsProcessing {
            private boolean enabled = true;
            private int queueCapacity = 500;
            private int workerCount = 2;
            private int batchSize = 50;
            private long interval = 60000; // 1 minute
            private int stuckThresholdMinutes = 10;
            private long recoveryInterval = 300000; // 5 minutes
        }

        @Data
        public static class BriefingExecution {
            private boolean enabled = true;
        }
    }

    @Data
    public static class Ai {
        @NotBlank
        private String provider = "mock";
    }
}
