package com.pochampally.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@Profile("!test")
@Slf4j
public class CacheConfig implements CachingConfigurer {

    public static final String PRODUCTS_CACHE = "products";
    public static final String PRODUCT_LISTS_CACHE = "productLists";
    public static final String BANNERS_CACHE = "cmsBanners";
    public static final String CATEGORIES_CACHE = "cmsCategories";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Setup ObjectMapper to handle Java date/time (Instant, etc.) properly in JSON
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // Configure default cache settings with 5 minutes TTL
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        // Specific configurations for our key caches
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put(PRODUCTS_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigurations.put(PRODUCT_LISTS_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigurations.put(BANNERS_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put(CATEGORIES_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * Fail-safe cache access: if Redis is unreachable or a stored value can't be
     * deserialized, log and fall through to the underlying method (DB) instead of
     * failing the request. A GET error is treated as a cache miss; PUT/EVICT/CLEAR
     * errors are swallowed. This keeps the site up even when the cache misbehaves.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache GET failed [{}::{}] — falling back to source: {}", cache.getName(), key, ex.getMessage());
            }
            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed [{}::{}]: {}", cache.getName(), key, ex.getMessage());
            }
            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache EVICT failed [{}::{}]: {}", cache.getName(), key, ex.getMessage());
            }
            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.warn("Cache CLEAR failed [{}]: {}", cache.getName(), ex.getMessage());
            }
        };
    }
}
