package com.pochampally.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "video/mp4", "video/webm"
    );
    private static final long MAX_VIDEO_SIZE = 50L * 1024 * 1024; // 50MB

    private final S3Client s3Client;

    @Value("${cloudflare.r2.bucket}")
    private String bucket;

    @Value("${cloudflare.r2.public-domain}")
    private String publicDomain;

    /**
     * Upload a video to Cloudflare R2.
     *
     * @param file      the multipart video file
     * @param productId the product this video belongs to
     * @return the public CDN URL for the uploaded video
     */
    public String uploadVideo(MultipartFile file, String productId) {
        validateVideo(file);

        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        String key = "videos/" + productId + "/" + UUID.randomUUID() + extension;

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            String cdnUrl = "https://" + publicDomain + "/" + key;
            log.info("Uploaded video for product {}: {}", productId, cdnUrl);
            return cdnUrl;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload video: " + e.getMessage(), e);
        }
    }

    private void validateVideo(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Video file is empty");
        }

        if (file.getSize() > MAX_VIDEO_SIZE) {
            throw new IllegalArgumentException(
                    "Video size exceeds maximum of 50MB. Got: " + (file.getSize() / (1024 * 1024)) + "MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Invalid video type: " + contentType + ". Allowed: MP4, WebM");
        }

        validateVideoMagicBytes(file, contentType);
    }

    /**
     * Validates video file magic bytes match the declared Content-Type.
     */
    private void validateVideoMagicBytes(MultipartFile file, String contentType) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            int bytesRead = is.read(header);
            if (bytesRead < 4) {
                throw new IllegalArgumentException("Video file too small to identify");
            }

            boolean valid = switch (contentType) {
                case "video/mp4" ->
                        // MP4 (ftyp box): 00 00 00 XX at offset 0, "ftyp" at offset 4-7
                        bytesRead >= 8
                                && (header[0] & 0xFF) == 0x00 && (header[1] & 0xFF) == 0x00 && (header[2] & 0xFF) == 0x00
                                && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p';
                case "video/webm" ->
                        // WebM (EBML): 1A 45 DF A3
                        bytesRead >= 4
                                && (header[0] & 0xFF) == 0x1A && (header[1] & 0xFF) == 0x45
                                && (header[2] & 0xFF) == 0xDF && (header[3] & 0xFF) == 0xA3;
                default -> false;
            };

            if (!valid) {
                throw new IllegalArgumentException(
                        "Video content does not match declared Content-Type: " + contentType);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read video for validation", e);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".mp4";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }
}
