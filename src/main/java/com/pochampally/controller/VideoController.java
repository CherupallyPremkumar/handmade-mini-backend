package com.pochampally.controller;

import com.pochampally.service.ProductService;
import com.pochampally.service.VideoStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/videos")
@RequiredArgsConstructor
@Slf4j
public class VideoController {

    private final VideoStorageService videoStorageService;
    private final ProductService productService;

    /**
     * Upload a video for a product.
     * Accepts .mp4, .webm files up to 50MB.
     * Stores in R2 under videos/{productId}/{uuid}.ext and saves CDN URL to product.videoUrl.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadVideo(
            @RequestParam("productId") String productId,
            @RequestParam("file") MultipartFile file) {

        // Validate product exists
        productService.getById(productId);

        String cdnUrl = videoStorageService.uploadVideo(file, productId);
        productService.updateVideoUrl(productId, cdnUrl);

        log.info("Uploaded video for product {}: {}", productId, cdnUrl);

        return ResponseEntity.ok(Map.of(
                "videoUrl", cdnUrl
        ));
    }
}
