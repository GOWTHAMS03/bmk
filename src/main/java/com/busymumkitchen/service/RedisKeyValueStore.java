package com.busymumkitchen.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Redis-backed {@link KeyValueStore}. Active when {@code redis.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "redis.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RedisKeyValueStore implements KeyValueStore {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Object getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void setValue(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    @Override
    public Map<Object, Object> getHashEntries(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    @Override
    public Object getHashValue(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    @Override
    public void putHashValue(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    @Override
    public void deleteHashValues(String key, String... hashKeys) {
        Object[] keys = hashKeys;
        redisTemplate.opsForHash().delete(key, keys);
    }

    @Override
    public void expire(String key, Duration ttl) {
        redisTemplate.expire(key, ttl);
    }

    @Override
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }
}

