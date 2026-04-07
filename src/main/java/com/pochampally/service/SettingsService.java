package com.pochampally.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pochampally.entity.AppSetting;
import com.pochampally.repository.AppSettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    private final AppSettingRepository settingRepository;
    private final ObjectMapper objectMapper;

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
    }

    public String get(String key) {
        return settingRepository.findById(key)
                .map(AppSetting::getValue)
                .orElse("");
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
        return settingRepository.save(setting);
    }
}
