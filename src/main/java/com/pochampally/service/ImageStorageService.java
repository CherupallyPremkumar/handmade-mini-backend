package com.pochampally.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageStorageService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of("video/mp4", "video/webm");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloudflare.r2.bucket}")
    private String bucket;

    @Value("${cloudflare.r2.public-domain}")
    private String publicDomain;

    /**
     * Generate a presigned PUT URL for direct browser-to-R2 image upload.
     * The file never touches this server.
     */
    private static final long MAX_VIDEO_SIZE = 100L * 1024 * 1024;  // 100MB

    public Map<String, String> generatePresignedUploadUrl(String productId, String contentType, String filename) {
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Invalid image type: " + contentType + ". Allowed: JPEG, PNG, WebP");
        }

        String extension = extractExtension(filename);
        String key = "products/" + productId + "/" + UUID.randomUUID() + extension;
        String cdnUrl = "https://" + publicDomain + "/" + key;

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(putRequest)
                .build();

        String presignedUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

        log.info("Generated presigned image URL for product {}: {}", productId, key);
        return Map.of("uploadUrl", presignedUrl, "cdnUrl", cdnUrl, "key", key);
    }

    /**
     * Generate a presigned PUT URL for direct browser-to-R2 video upload.
     */
    public Map<String, String> generatePresignedVideoUrl(String productId, String contentType, String filename) {
        if (!ALLOWED_VIDEO_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Invalid video type: " + contentType + ". Allowed: MP4, WebM");
        }

        String extension = extractExtension(filename);
        String key = "videos/" + productId + "/" + UUID.randomUUID() + extension;
        String cdnUrl = "https://" + publicDomain + "/" + key;

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(30))
                .putObjectRequest(putRequest)
                .build();

        String presignedUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

        log.info("Generated presigned video URL for product {}: {}", productId, key);
        return Map.of("uploadUrl", presignedUrl, "cdnUrl", cdnUrl, "key", key);
    }

    /** Legacy: upload image through server (kept for backward compatibility) */
    public String uploadImage(MultipartFile file, String productId) {
        String key = "products/" + productId + "/" + UUID.randomUUID() + extractExtension(file.getOriginalFilename());
        try {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(file.getContentType()).contentLength(file.getSize()).build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return "https://" + publicDomain + "/" + key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload image", e);
        }
    }

    public void deleteImage(String imageUrl) {
        String key = extractKeyFromUrl(imageUrl);
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        log.info("Deleted from R2: {}", key);
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf('.'));
    }

    private String extractKeyFromUrl(String imageUrl) {
        String prefix = "https://" + publicDomain + "/";
        if (imageUrl.startsWith(prefix)) return imageUrl.substring(prefix.length());
        throw new IllegalArgumentException("Invalid URL: does not match public domain");
    }
}
