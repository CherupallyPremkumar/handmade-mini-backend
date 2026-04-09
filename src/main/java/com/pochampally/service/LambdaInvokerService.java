package com.pochampally.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;

import java.util.Map;

/**
 * Fire-and-forget invoker for AWS Lambda functions.
 *
 * Currently used for async video compression via the
 * dhanunjaiah-video-compressor Lambda.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LambdaInvokerService {

    private final LambdaAsyncClient lambdaClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.lambda.video-compressor-arn:}")
    private String videoCompressorArn;

    /**
     * Asynchronously invoke the video compression Lambda.
     * Returns immediately; the Lambda runs in the background and will call
     * the backend webhook when done.
     *
     * @return true if invocation was accepted, false if Lambda not configured
     *         or invocation failed (non-blocking — failure is logged)
     */
    public boolean invokeVideoCompression(String productId, String tempKey) {
        if (videoCompressorArn == null || videoCompressorArn.isBlank()) {
            log.warn("LAMBDA_VIDEO_COMPRESSOR_ARN not configured — skipping compression for product {}",
                    productId);
            return false;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "productId", productId,
                    "tempKey", tempKey
            );
            String json = objectMapper.writeValueAsString(payload);

            InvokeRequest request = InvokeRequest.builder()
                    .functionName(videoCompressorArn)
                    .invocationType(InvocationType.EVENT) // async, fire-and-forget
                    .payload(SdkBytes.fromUtf8String(json))
                    .build();

            lambdaClient.invoke(request)
                    .thenAccept(response -> {
                        if (response.statusCode() == 202) {
                            log.info("Video compression Lambda invoked for product {} (tempKey={})",
                                    productId, tempKey);
                        } else {
                            log.error("Lambda invocation returned status {} for product {}",
                                    response.statusCode(), productId);
                        }
                    })
                    .exceptionally(err -> {
                        log.error("Failed to invoke Lambda for product {}: {}",
                                productId, err.getMessage());
                        return null;
                    });

            return true;
        } catch (Exception e) {
            log.error("Error invoking video compression Lambda for product {}: {}",
                    productId, e.getMessage(), e);
            return false;
        }
    }
}
