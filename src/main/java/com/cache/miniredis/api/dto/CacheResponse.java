package com.cache.miniredis.api.dto;

/**
 * CacheResponse -- Generic immutable response DTO for all CacheController endpoints.
 *
 * A single response type for GET, POST, and DELETE keeps the API surface consistent.
 * The 'found' flag disambiguates cache hits from misses without requiring callers to
 * interpret HTTP status codes alone.
 *
 * JSON SCHEMA:
 *   { "key": "string", "value": "any | null", "found": "boolean" }
 *
 * USAGE BY ENDPOINT:
 *   GET hit:      { "key": "k", "value": <val>, "found": true  }
 *   GET miss:     { "key": "k", "value": null,  "found": false }
 *   POST:         { "key": "k", "value": <val>, "found": true  }
 *   DELETE hit:   { "key": "k", "value": null,  "found": false }
 *   DELETE miss:  { "key": "k", "value": null,  "found": false }
 *
 * @param <V>   type of the cached value field
 * @param key   cache key this response pertains to
 * @param value retrieved or stored value; null on miss or after deletion
 * @param found true if key was present and not expired at access time
 *
 * @author  Mini-Redis Engineering Team
 * @version 1.0.0
 * @since   Java 21
 */
public record CacheResponse<V>(
        String  key,
        V       value,
        boolean found
) {

    /**
     * Factory: cache hit -- key found with a non-null value.
     *
     * @param <V>   value type
     * @param key   the cache key
     * @param value the cached value
     * @return CacheResponse with found=true
     */
    public static <V> CacheResponse<V> hit(String key, V value) {
        return new CacheResponse<>(key, value, true);
    }

    /**
     * Factory: cache miss -- key absent or expired.
     *
     * @param <V> value type
     * @param key the queried key
     * @return CacheResponse with value=null and found=false
     */
    public static <V> CacheResponse<V> miss(String key) {
        return new CacheResponse<>(key, null, false);
    }
}
