package com.cache.miniredis.eviction;

/**
 * EvictionPolicy - Strategy contract for cache eviction.
 * NOT thread-safe; callers must hold the write lock.
 */
public interface EvictionPolicy<K> {
    void keyAccessed(K key);
    void keyAdded(K key);
    K    evictNext();
}
