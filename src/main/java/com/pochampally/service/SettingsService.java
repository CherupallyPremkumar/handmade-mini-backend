package com.pochampally.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pochampally.entity.AppSetting;
import com.pochampally.entity.Category;
import com.pochampally.repository.AppSettingRepository;
import com.pochampally.repository.CategoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    private final AppSettingRepository settingRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    private static final long CACHE_TTL_SECONDS = 60;
    private final ConcurrentHashMap<String, CachedValue> cache = new ConcurrentHashMap<>();

    private record CachedValue(String value, Instant expiry) {
        boolean isExpired() { return Instant.now().isAfter(expiry); }
    }

    /**
     * On startup, seed any missing settings from db/seed/app-settings.json.
     * Already-existing keys are NOT overwritten — admin changes are preserved.
     */
    @PostConstruct
    @Transactional
    public void seedFromJson() {
        try {
            InputStream is = new ClassPathResource("db/seed/app-settings.json").getInputStream();
            List<Map<String, String>> seeds = objectMapper.readValue(is, new TypeReference<>() {});

            int seeded = 0;
            for (Map<String, String> seed : seeds) {
                String key = seed.get("key");
                if (!settingRepository.existsById(key)) {
                    settingRepository.save(AppSetting.builder()
                            .key(key)
                            .value(seed.get("value"))
                            .description(seed.get("description"))
                            .build());
                    seeded++;
                }
            }
            if (seeded > 0) {
                log.info("Seeded {} missing app settings from JSON", seeded);
            }
        } catch (Exception e) {
            log.warn("Could not seed app settings from JSON: {}", e.getMessage());
        }

        // Seed categories from JSON (only if categories table is empty)
        try {
            if (categoryRepository.count() == 0) {
                InputStream catIs = new ClassPathResource("db/seed/categories.json").getInputStream();
                List<Map<String, String>> cats = objectMapper.readValue(catIs, new TypeReference<>() {});
                int pos = 0;
                for (Map<String, String> cat : cats) {
                    categoryRepository.save(Category.builder()
                            .name(cat.get("name"))
                            .slug(cat.get("slug"))
                            .description(cat.get("description"))
                            .filterKey(cat.getOrDefault("filterKey", ""))
                            .filterValue(cat.getOrDefault("filterValue", ""))
                            .position(pos++)
                            .showOnHome(false)
                            .isActive(true)
                            .build());
                }
                log.info("Seeded {} categories from JSON", cats.size());
            }
        } catch (Exception e) {
            log.warn("Could not seed categories from JSON: {}", e.getMessage());
        }
    }

    public String get(String key) {
        CachedValue cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }

        String value = settingRepository.findById(key)
                .map(AppSetting::getValue)
                .orElse("");

        cache.put(key, new CachedValue(value, Instant.now().plusSeconds(CACHE_TTL_SECONDS)));
        return value;
    }

    public int getInt(String key) {
        String val = get(key);
        if (val.isEmpty()) return 0;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public long getLong(String key) {
        String val = get(key);
        if (val.isEmpty()) return 0;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 0;
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
        AppSetting saved = settingRepository.save(setting);
        cache.remove(key);
        return saved;
    }
}
