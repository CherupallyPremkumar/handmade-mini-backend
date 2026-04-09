package com.pochampally.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;

/**
 * AWS Lambda async client — used to fire-and-forget video compression jobs.
 *
 * Uses explicit access keys if AWS_LAMBDA_ACCESS_KEY is set (dev/local),
 * otherwise falls back to the default credential chain (EC2 instance role in prod).
 */
@Configuration
public class LambdaConfig {

    @Value("${aws.region:ap-south-1}")
    private String region;

    @Value("${aws.lambda.access-key:}")
    private String accessKey;

    @Value("${aws.lambda.secret-key:}")
    private String secretKey;

    @Bean
    public LambdaAsyncClient lambdaAsyncClient() {
        var builder = LambdaAsyncClient.builder().region(Region.of(region));

        if (accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        } else {
            // Use EC2 instance role or default chain
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }
}
