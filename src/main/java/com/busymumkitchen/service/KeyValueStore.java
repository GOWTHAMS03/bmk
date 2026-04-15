package com.busymumkitchen.service;

import java.time.Duration;
import java.util.Map;

/**
 * Abstraction over key-value storage used by AuthService (OTP) and
 * CartService (shopping cart).
 * <p>
 * Backed by Redis when {@code redis.enabled=true}, or by a simple
 * in-memory {@link java.util.concurrent.ConcurrentHashMap} during
 * local development.
 */
public interface KeyValueStore {

    // ── value operations ──────────────────────────────────────────────

    Object getValue(String key);

    void setValue(String key, Object value, Duration ttl);

    Long increment(String key);

    // ── hash operations ───────────────────────────────────────────────

    Map<Object, Object> getHashEntries(String key);

    Object getHashValue(String key, String hashKey);

    void putHashValue(String key, String hashKey, Object value);

    void deleteHashValues(String key, String... hashKeys);

    // ── key-level operations ──────────────────────────────────────────

    void expire(String key, Duration ttl);

    Boolean delete(String key);
}

