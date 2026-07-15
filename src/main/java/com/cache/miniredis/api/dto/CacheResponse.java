package com.cache.miniredis.api.dto;

/** Placeholder DTO - full REST API is post-Phase 4. */
public class CacheResponse {
    public boolean success;
    public String  message;
    public Object  data;

    public CacheResponse(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data    = data;
    }
}
