package com.busymumkitchen.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory {@link KeyValueStore} used during local development when
 * Redis is not available ({@code redis.enabled=false}, the default).
 * <p>
 * Entries honour TTLs via lazy expiration: stale entries are evicted on
 * next access and periodically by a background daemon thread.
 */
@Component
@ConditionalOnProperty(name = "redis.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class InMemoryKeyValueStore implements KeyValueStore {

    /** Stored values (String → Object or Map for hashes). */
    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();

    /** Expiry timestamps (key → Instant). Missing means no expiry. */
    private final ConcurrentHashMap<String, Instant> expiries = new ConcurrentHashMap<>();

    public InMemoryKeyValueStore() {
        log.info("[DEV MODE] Using in-memory key-value store (Redis disabled)");

        // Background cleanup every 60 s
        Thread cleaner = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    //noinspection BusyWait
                    Thread.sleep(60_000);
                    evictExpired();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "kv-store-cleaner");
        cleaner.setDaemon(true);
        cleaner.start();
    }

    // ── value operations ──────────────────────────────────────────────

    @Override
    public Object getValue(String key) {
        if (isExpired(key)) { evict(key); return null; }
        return store.get(key);
    }

    @Override
    public void setValue(String key, Object value, Duration ttl) {
        store.put(key, value);
        if (ttl != null) {
            expiries.put(key, Instant.now().plus(ttl));
        }
    }

    @Override
    public Long increment(String key) {
        if (isExpired(key)) { evict(key); }
        Object current = store.get(key);
        long next;
        if (current instanceof Number) {
            next = ((Number) current).longValue() + 1;
        } else {
            next = 1;
        }
        store.put(key, (int) next); // store as Integer to match Redis JSON deserialisation
        return next;
    }

    // ── hash operations ───────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public Map<Object, Object> getHashEntries(String key) {
        if (isExpired(key)) { evict(key); return Collections.emptyMap(); }
        Object raw = store.get(key);
        if (raw instanceof Map) {
            return new LinkedHashMap<>((Map<Object, Object>) raw);
        }
        return Collections.emptyMap();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object getHashValue(String key, String hashKey) {
        if (isExpired(key)) { evict(key); return null; }
        Object raw = store.get(key);
        if (raw instanceof Map) {
            return ((Map<Object, Object>) raw).get(hashKey);
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void putHashValue(String key, String hashKey, Object value) {
        store.compute(key, (k, existing) -> {
            Map<Object, Object> map;
            if (existing instanceof Map) {
                map = (Map<Object, Object>) existing;
            } else {
                map = new ConcurrentHashMap<>();
            }
            map.put(hashKey, value);
            return map;
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void deleteHashValues(String key, String... hashKeys) {
        Object raw = store.get(key);
        if (raw instanceof Map) {
            Map<Object, Object> map = (Map<Object, Object>) raw;
            for (String hk : hashKeys) {
                map.remove(hk);
            }
        }
    }

    // ── key-level operations ──────────────────────────────────────────

    @Override
    public void expire(String key, Duration ttl) {
        if (store.containsKey(key) && ttl != null) {
            expiries.put(key, Instant.now().plus(ttl));
        }
    }

    @Override
    public Boolean delete(String key) {
        expiries.remove(key);
        return store.remove(key) != null;
    }

    // ── helpers ───────────────────────────────────────────────────────

    private boolean isExpired(String key) {
        Instant exp = expiries.get(key);
        return exp != null && Instant.now().isAfter(exp);
    }

    private void evict(String key) {
        store.remove(key);
        expiries.remove(key);
    }

    private void evictExpired() {
        Instant now = Instant.now();
        expiries.forEach((key, exp) -> {
            if (now.isAfter(exp)) { evict(key); }
        });
    }
}

