package com.busymumkitchen.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
public class AwsSnsConfig {

    @Value("${aws.sns.access-key:}")
    private String accessKey;

    @Value("${aws.sns.secret-key:}")
    private String secretKey;

    @Value("${AWS_REGION:ap-south-1}")
    private String region;

    @Bean
    @ConditionalOnProperty(name = "aws.sns.enabled", havingValue = "true", matchIfMissing = true)
    public SnsClient snsClient() {
        var builder = SnsClient.builder().region(Region.of(region));

        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);
            builder.credentialsProvider(StaticCredentialsProvider.create(creds));
        }

        return builder.build();
    }
}
