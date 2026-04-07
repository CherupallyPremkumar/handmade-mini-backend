package com.pochampally.service;

import com.pochampally.entity.AppSetting;
import com.pochampally.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final AppSettingRepository settingRepository;

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("max_addresses_per_user", "10"),
            Map.entry("max_pending_orders", "3"),
            Map.entry("free_shipping_threshold", "99900"),
            Map.entry("shipping_cost", "9900"),
            Map.entry("min_product_images", "3"),
            Map.entry("min_product_videos", "1"),
            Map.entry("require_product_description", "true"),
            Map.entry("require_secondary_description", "true")
    );

    public String get(String key) {
        return settingRepository.findById(key)
                .map(AppSetting::getValue)
                .orElse(DEFAULTS.getOrDefault(key, ""));
    }

    public int getInt(String key) {
        try {
            return Integer.parseInt(get(key));
        } catch (NumberFormatException e) {
            return Integer.parseInt(DEFAULTS.getOrDefault(key, "0"));
        }
    }

    public long getLong(String key) {
        try {
            return Long.parseLong(get(key));
        } catch (NumberFormatException e) {
            return Long.parseLong(DEFAULTS.getOrDefault(key, "0"));
        }
    }

    public List<AppSetting> getAll() {
        return settingRepository.findAll();
    }

    @Transactional
    public AppSetting update(String key, String value) {
        AppSetting setting = settingRepository.findById(key)
                .orElseThrow(() -> new IllegalArgumentException("Setting not found: " + key));
        setting.setValue(value);
        return settingRepository.save(setting);
    }
}
