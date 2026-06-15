package com.cache.miniredis.api.dto;

/**
 * CachePutRequest -- Immutable request DTO for POST /api/v1/cache/.
 *
 * Modelled as a Java 21 record for immutability and zero boilerplate.
 * Jackson deserialises the incoming JSON object via the canonical constructor.
 *
 * JSON SCHEMA:
 *   {
 *     "key":       "string (required, non-empty)",
 *     "value":     "any   (required)",
 *     "ttlMillis": "long  (optional; 0 = immortal, omitted defaults to 0)"
 *   }
 *
 * @param key       non-null, non-empty string cache key
 * @param value     any JSON-serialisable object
 * @param ttlMillis TTL in milliseconds; 0 or negative signals no expiry
 *
 * @author  Mini-Redis Engineering Team
 * @version 1.0.0
 * @since   Java 21
 */
public record CachePutRequest(
        String key,
        Object value,
        long   ttlMillis
) {}
