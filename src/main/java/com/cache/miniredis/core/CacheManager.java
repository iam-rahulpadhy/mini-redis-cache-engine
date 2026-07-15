package com.cache.miniredis.core;

/**
 * CacheManager - Primary contract for the Mini-Redis in-memory cache.
 * All implementations must be thread-safe.
 */
public interface CacheManager<K, V> {
    void    put(K key, V value, long ttlMillis);
    V       get(K key);
    boolean remove(K key);
    void    clear();
    int     size();
}
