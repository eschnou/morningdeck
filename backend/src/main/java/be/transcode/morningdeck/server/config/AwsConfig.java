package be.transcode.morningdeck.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

@Configuration
public class AwsConfig {

    @Value("${spring.cloud.aws.region.static}")
    private String awsRegion;

    @Bean
    @ConditionalOnProperty(name = "spring.cloud.aws.credentials.instance-profile", havingValue = "true")
    public SesClient sesClient() {
        return SesClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cloud.aws.credentials.instance-profile", havingValue = "false")
    public SesClient localSesClient() {
        return SesClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();
    }
}
