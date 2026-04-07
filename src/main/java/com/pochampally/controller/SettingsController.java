package com.pochampally.controller;

import com.pochampally.entity.AppSetting;
import com.pochampally.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public ResponseEntity<List<AppSetting>> getAll() {
        return ResponseEntity.ok(settingsService.getAll());
    }

    @GetMapping("/product-rules")
    public ResponseEntity<Map<String, Object>> getProductRules() {
        return ResponseEntity.ok(Map.of(
                "minImages", settingsService.getInt("min_product_images"),
                "minVideos", settingsService.getInt("min_product_videos"),
                "requireDescription", "true".equals(settingsService.get("require_product_description")),
                "requireSecondaryDescription", "true".equals(settingsService.get("require_secondary_description"))
        ));
    }

    @PutMapping("/{key}")
    public ResponseEntity<AppSetting> update(@PathVariable String key, @RequestBody Map<String, String> body) {
        String value = body.get("value");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Value is required");
        }
        return ResponseEntity.ok(settingsService.update(key, value));
    }
}
