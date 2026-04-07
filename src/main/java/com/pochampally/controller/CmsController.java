package com.pochampally.controller;

import com.pochampally.entity.Banner;
import com.pochampally.entity.Category;
import com.pochampally.repository.BannerRepository;
import com.pochampally.repository.CategoryRepository;
import com.pochampally.service.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CmsController {

    private final BannerRepository bannerRepository;
    private final CategoryRepository categoryRepository;
    private final ImageStorageService imageStorageService;
    private final com.pochampally.service.SettingsService settingsService;

    // ═══ Public: Storefront ═══

    @GetMapping("/api/cms/banners")
    public ResponseEntity<Map<String, Object>> activeBanners() {
        return ResponseEntity.ok(Map.of(
                "banners", bannerRepository.findByIsActiveTrueOrderByPositionAsc(),
                "scrollSeconds", settingsService.getInt("banner_scroll_seconds")
        ));
    }

    @GetMapping("/api/cms/categories")
    public ResponseEntity<List<Category>> activeCategories() {
        return ResponseEntity.ok(categoryRepository.findByIsActiveTrueOrderByPositionAsc());
    }

    @GetMapping("/api/cms/categories/home")
    public ResponseEntity<List<Category>> homeCategories() {
        return ResponseEntity.ok(categoryRepository.findByIsActiveTrueAndShowOnHomeTrueOrderByPositionAsc());
    }

    @GetMapping("/api/cms/categories/{slug}")
    public ResponseEntity<Category> categoryBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + slug)));
    }

    // ═══ Admin: Banners ═══

    @GetMapping("/api/admin/banners")
    public ResponseEntity<List<Banner>> allBanners() {
        return ResponseEntity.ok(bannerRepository.findAllByOrderByPositionAsc());
    }

    @PostMapping("/api/admin/banners")
    public ResponseEntity<Banner> createBanner(@RequestBody Banner banner) {
        if (banner.getImageUrl() == null || banner.getImageUrl().isBlank()) {
            throw new IllegalArgumentException("Image URL is required");
        }
        int maxBanners = settingsService.getInt("max_banners");
        if (maxBanners > 0 && bannerRepository.count() >= maxBanners) {
            throw new IllegalStateException("Maximum " + maxBanners + " banners allowed");
        }
        return ResponseEntity.ok(bannerRepository.save(banner));
    }

    @PutMapping("/api/admin/banners/{id}")
    public ResponseEntity<Banner> updateBanner(@PathVariable String id, @RequestBody Banner updates) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Banner not found"));
        if (updates.getTitle() != null) banner.setTitle(updates.getTitle());
        if (updates.getSubtitle() != null) banner.setSubtitle(updates.getSubtitle());
        if (updates.getImageUrl() != null) banner.setImageUrl(updates.getImageUrl());
        if (updates.getMobileImageUrl() != null) banner.setMobileImageUrl(updates.getMobileImageUrl());
        if (updates.getLinkUrl() != null) banner.setLinkUrl(updates.getLinkUrl());
        if (updates.getLinkText() != null) banner.setLinkText(updates.getLinkText());
        if (updates.getTextColor() != null) banner.setTextColor(updates.getTextColor());
        if (updates.getPosition() != null) banner.setPosition(updates.getPosition());
        if (updates.getIsActive() != null) banner.setIsActive(updates.getIsActive());
        return ResponseEntity.ok(bannerRepository.save(banner));
    }

    @DeleteMapping("/api/admin/banners/{id}")
    public ResponseEntity<Void> deleteBanner(@PathVariable String id) {
        bannerRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/admin/banners/{id}/toggle")
    public ResponseEntity<Banner> toggleBanner(@PathVariable String id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Banner not found"));
        banner.setIsActive(!banner.getIsActive());
        return ResponseEntity.ok(bannerRepository.save(banner));
    }

    // ═══ Admin: Categories ═══

    @GetMapping("/api/admin/categories")
    public ResponseEntity<List<Category>> allCategories() {
        return ResponseEntity.ok(categoryRepository.findAllByOrderByPositionAsc());
    }

    @PostMapping("/api/admin/categories")
    public ResponseEntity<Category> createCategory(@RequestBody Category category) {
        if (category.getName() == null || category.getName().isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        if (category.getSlug() == null || category.getSlug().isBlank()) {
            category.setSlug(category.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", ""));
        }
        return ResponseEntity.ok(categoryRepository.save(category));
    }

    @PutMapping("/api/admin/categories/{id}")
    public ResponseEntity<Category> updateCategory(@PathVariable String id, @RequestBody Category updates) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        if (updates.getName() != null) category.setName(updates.getName());
        if (updates.getSlug() != null) category.setSlug(updates.getSlug());
        if (updates.getDescription() != null) category.setDescription(updates.getDescription());
        if (updates.getImageUrl() != null) category.setImageUrl(updates.getImageUrl());
        if (updates.getFilterKey() != null) category.setFilterKey(updates.getFilterKey());
        if (updates.getFilterValue() != null) category.setFilterValue(updates.getFilterValue());
        if (updates.getPosition() != null) category.setPosition(updates.getPosition());
        if (updates.getShowOnHome() != null) category.setShowOnHome(updates.getShowOnHome());
        if (updates.getIsActive() != null) category.setIsActive(updates.getIsActive());
        return ResponseEntity.ok(categoryRepository.save(category));
    }

    @DeleteMapping("/api/admin/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable String id) {
        categoryRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/admin/categories/{id}/toggle")
    public ResponseEntity<Category> toggleCategory(@PathVariable String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        category.setIsActive(!category.getIsActive());
        return ResponseEntity.ok(categoryRepository.save(category));
    }

    // ═══ Admin: CMS Image Upload ═══

    /**
     * Generate presigned URL for CMS image upload (banners/categories).
     * type: "banner" or "category"
     */
    @PostMapping("/api/admin/cms/presign-image")
    public ResponseEntity<?> presignCmsImage(@RequestBody Map<String, String> body) {
        String type = body.get("type");
        String contentType = body.get("contentType");
        String filename = body.get("filename");

        if (type == null || contentType == null) {
            throw new IllegalArgumentException("type and contentType required");
        }
        if (!"banner".equals(type) && !"category".equals(type)) {
            throw new IllegalArgumentException("type must be 'banner' or 'category'");
        }

        var result = imageStorageService.generatePresignedCmsUrl(type, contentType, filename);
        return ResponseEntity.ok(result);
    }
}
