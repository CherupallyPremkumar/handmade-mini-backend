package com.pochampally.controller;

import com.pochampally.service.ImageStorageService;
import com.pochampally.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Media endpoints using presigned URLs.
 * Files go directly from browser to R2 — never touch this server.
 */
@RestController
@RequestMapping("/api/admin/media")
@RequiredArgsConstructor
@Slf4j
public class MediaController {

    private final ImageStorageService imageStorageService;
    private final ProductService productService;

    /**
     * Generate a presigned upload URL for an image.
     * Frontend uploads directly to R2 using this URL.
     */
    @PostMapping("/presign-image")
    public ResponseEntity<?> presignImage(@RequestBody Map<String, String> body) {
        String productId = body.get("productId");
        String contentType = body.get("contentType");
        String filename = body.get("filename");

        if (productId == null || contentType == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "productId and contentType required"));
        }

        // Validate product exists
        productService.getById(productId);

        // Check image count
        var product = productService.getById(productId);
        int current = product.getImages() != null ? product.getImages().size() : 0;
        if (current >= 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Maximum 6 images per product"));
        }

        var result = imageStorageService.generatePresignedUploadUrl(productId, contentType, filename);
        return ResponseEntity.ok(result);
    }

    /**
     * Generate a presigned upload URL for a video.
     */
    @PostMapping("/presign-video")
    public ResponseEntity<?> presignVideo(@RequestBody Map<String, String> body) {
        String productId = body.get("productId");
        String contentType = body.get("contentType");
        String filename = body.get("filename");

        if (productId == null || contentType == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "productId and contentType required"));
        }

        productService.getById(productId);

        var result = imageStorageService.generatePresignedVideoUrl(productId, contentType, filename);
        return ResponseEntity.ok(result);
    }

    /**
     * Confirm upload complete — save the URL to the product.
     */
    @PostMapping("/confirm-image")
    public ResponseEntity<?> confirmImage(@RequestBody Map<String, String> body) {
        String productId = body.get("productId");
        String cdnUrl = body.get("cdnUrl");

        if (productId == null || cdnUrl == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "productId and cdnUrl required"));
        }

        var product = productService.getById(productId);
        List<String> images = product.getImages() != null ? new ArrayList<>(product.getImages()) : new ArrayList<>();
        images.add(cdnUrl);
        productService.updateImages(productId, images);

        log.info("Confirmed image for product {}: {}", productId, cdnUrl);
        return ResponseEntity.ok(Map.of("totalImages", images.size()));
    }

    /**
     * Confirm video upload complete — save the URL to the product.
     */
    @PostMapping("/confirm-video")
    public ResponseEntity<?> confirmVideo(@RequestBody Map<String, String> body) {
        String productId = body.get("productId");
        String cdnUrl = body.get("cdnUrl");

        if (productId == null || cdnUrl == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "productId and cdnUrl required"));
        }

        productService.updateVideoUrl(productId, cdnUrl);

        log.info("Confirmed video for product {}: {}", productId, cdnUrl);
        return ResponseEntity.ok(Map.of("videoUrl", cdnUrl));
    }

    /**
     * Delete an image from R2 and product.
     */
    @DeleteMapping("/image")
    public ResponseEntity<?> deleteImage(@RequestParam String productId, @RequestParam String imageUrl) {
        var product = productService.getById(productId);
        List<String> images = product.getImages() != null ? new ArrayList<>(product.getImages()) : new ArrayList<>();

        if (!images.remove(imageUrl)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Image not found on product"));
        }

        imageStorageService.deleteImage(imageUrl);
        productService.updateImages(productId, images);

        return ResponseEntity.ok(Map.of("totalImages", images.size()));
    }
}
