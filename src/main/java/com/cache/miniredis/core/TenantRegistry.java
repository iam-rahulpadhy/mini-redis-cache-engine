package com.cache.miniredis.core;

import com.cache.miniredis.concurrency.CacheLockManager;
import com.cache.miniredis.eviction.LRUEvictionStrategy;

import java.util.concurrent.ConcurrentHashMap;

/**
 * TenantRegistry - Manages isolated MiniRedisEngine namespaces for multi-tenancy.
 */
public class TenantRegistry<K, V> {

    private final ConcurrentHashMap<String, MiniRedisEngine<K, V>> namespaces;
    private final int defaultCapacity;

    public TenantRegistry(int defaultCapacity) {
        if (defaultCapacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.defaultCapacity = defaultCapacity;
        this.namespaces = new ConcurrentHashMap<>();
    }

    /**
     * Gets the cache engine for a specific tenant namespace, creating it if it doesn't exist.
     */
    public MiniRedisEngine<K, V> getCache(String namespace) {
        return namespaces.computeIfAbsent(namespace, k -> 
            new MiniRedisEngine<>(defaultCapacity, new LRUEvictionStrategy<>(), new CacheLockManager())
        );
    }

    /**
     * Completely removes a tenant's cache from memory.
     */
    public void dropCache(String namespace) {
        MiniRedisEngine<K, V> cache = namespaces.remove(namespace);
        if (cache != null) {
            cache.clear();
        }
    }
    
    /**
     * Gets the number of active namespaces.
     */
    public int getNamespaceCount() {
        return namespaces.size();
    }
}
