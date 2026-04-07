package com.pochampally.bdd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pochampally.service.RazorpayService;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SpringTestConfig.Overrides.class)
public class SpringTestConfig {

    /**
     * Replace external services with test doubles.
     * RazorpayService calls real Razorpay API — we override with a fake.
     * S3Client connects to Cloudflare R2 — we provide a dummy.
     */
    @TestConfiguration
    static class Overrides {

        private static final AtomicLong SEQ = new AtomicLong();

        @Bean
        @Primary
        public RazorpayService fakeRazorpay(ObjectMapper mapper) {
            return new RazorpayService("rzp_test_fake", "fake", "fake", mapper) {
                @Override
                public String createRazorpayOrder(long amount, String receipt) {
                    return "order_test_" + SEQ.incrementAndGet();
                }

                @Override
                public boolean verifyPaymentSignature(String orderId, String paymentId, String sig) {
                    return "valid".equals(sig);
                }

                @Override
                public boolean verifyWebhookSignature(String body, String sig) {
                    return "valid".equals(sig);
                }

                @Override
                public String getKeyId() {
                    return "rzp_test_fake";
                }
            };
        }

        @Bean
        @Primary
        public S3Client dummyS3() {
            return S3Client.builder()
                    .endpointOverride(URI.create("http://localhost:1"))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("x", "x")))
                    .region(Region.US_EAST_1)
                    .forcePathStyle(true)
                    .build();
        }

        @Bean
        @Primary
        public software.amazon.awssdk.services.s3.presigner.S3Presigner dummyPresigner() {
            return software.amazon.awssdk.services.s3.presigner.S3Presigner.builder()
                    .endpointOverride(URI.create("http://localhost:1"))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("x", "x")))
                    .region(Region.US_EAST_1)
                    .build();
        }
    }
}
