package com.pochampally.controller;

import com.pochampally.entity.Product;
import com.pochampally.service.ImageStorageService;
import com.pochampally.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/images")
@RequiredArgsConstructor
@Slf4j
public class ImageController {

    private static final int MAX_IMAGES_PER_PRODUCT = 6;

    private final ImageStorageService imageStorageService;
    private final ProductService productService;

    /**
     * Upload a single image for a product.
     * The image is stored in Cloudflare R2 and the CDN URL is added to the product's images array.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadImage(
            @RequestParam("productId") String productId,
            @RequestParam("file") MultipartFile file) {

        Product product = productService.getById(productId);
        List<String> images = product.getImages() != null ? new ArrayList<>(product.getImages()) : new ArrayList<>();

        if (images.size() >= MAX_IMAGES_PER_PRODUCT) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Maximum " + MAX_IMAGES_PER_PRODUCT + " images allowed per product"
            ));
        }

        String cdnUrl = imageStorageService.uploadImage(file, productId);
        images.add(cdnUrl);
        productService.updateImages(productId, images);

        log.info("Uploaded image for product {}: {}", productId, cdnUrl);

        return ResponseEntity.ok(Map.of(
                "imageUrl", cdnUrl,
                "totalImages", images.size()
        ));
    }

    /**
     * Upload multiple images for a product at once.
     */
    @PostMapping("/upload-multiple")
    public ResponseEntity<Map<String, Object>> uploadMultipleImages(
            @RequestParam("productId") String productId,
            @RequestParam("files") List<MultipartFile> files) {

        Product product = productService.getById(productId);
        List<String> images = product.getImages() != null ? new ArrayList<>(product.getImages()) : new ArrayList<>();

        int availableSlots = MAX_IMAGES_PER_PRODUCT - images.size();
        if (files.size() > availableSlots) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Can only upload " + availableSlots + " more image(s). Maximum " + MAX_IMAGES_PER_PRODUCT + " per product."
            ));
        }

        List<String> uploadedUrls = new ArrayList<>();
        for (MultipartFile file : files) {
            String cdnUrl = imageStorageService.uploadImage(file, productId);
            images.add(cdnUrl);
            uploadedUrls.add(cdnUrl);
        }

        productService.updateImages(productId, images);

        log.info("Uploaded {} images for product {}", uploadedUrls.size(), productId);

        return ResponseEntity.ok(Map.of(
                "imageUrls", uploadedUrls,
                "totalImages", images.size()
        ));
    }

    /**
     * Delete an image from a product.
     * Removes the URL from the product's images array and deletes the file from R2.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteImage(
            @RequestParam("productId") String productId,
            @RequestParam("imageUrl") String imageUrl) {

        Product product = productService.getById(productId);
        List<String> images = product.getImages() != null ? new ArrayList<>(product.getImages()) : new ArrayList<>();

        if (!images.remove(imageUrl)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Image URL not found on this product"
            ));
        }

        imageStorageService.deleteImage(imageUrl);
        productService.updateImages(productId, images);

        log.info("Deleted image from product {}: {}", productId, imageUrl);

        return ResponseEntity.ok(Map.of(
                "deleted", imageUrl,
                "totalImages", images.size()
        ));
    }
}
