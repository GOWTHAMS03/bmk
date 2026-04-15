package com.busymumkitchen.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Activated when {@code redis.enabled=false} (the default for local dev).
 * Provides a simple in-memory {@link ConcurrentMapCacheManager} so that
 * {@code @Cacheable} / {@code @CacheEvict} still work without Redis.
 */
@Configuration
@ConditionalOnProperty(name = "redis.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class RedisDisabledConfig {

    public RedisDisabledConfig() {
        log.info("[DEV MODE] Redis is DISABLED. Using in-memory cache and key-value store.");
    }

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("menu", "categories", "cart", "otp");
    }
}
