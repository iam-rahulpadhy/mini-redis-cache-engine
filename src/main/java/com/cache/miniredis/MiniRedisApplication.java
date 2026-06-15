package com.cache.miniredis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bootstrap entry point for the Mini-Redis in-memory cache engine.
 *
 * <p>Mini-Redis is a multi-tenant, thread-safe, TTL-aware caching system built entirely
 * on core Java concurrency primitives. Spring Boot is used exclusively for:
 * <ol>
 *   <li>DI wiring of the {@code CacheManager} singleton bean.</li>
 *   <li>Exposing the REST management API via {@code CacheController}.</li>
 *   <li>Scheduling the background TTL reaper via {@code @EnableScheduling}.</li>
 * </ol>
 * <p>All cache internals are pure Java -- no Spring caching abstraction is used.
 *
 * @author  Mini-Redis Engineering Team
 * @version 1.0.0
 * @since   Java 21
 */
@SpringBootApplication
@EnableScheduling
public class MiniRedisApplication {

    /**
     * JVM entry point. Bootstraps the Spring context and starts the embedded
     * Tomcat server on the port defined in application.properties (default 8080).
     *
     * @param args command-line arguments; supports --server.port and other overrides
     */
    public static void main(String[] args) {
        SpringApplication.run(MiniRedisApplication.class, args);
    }
}
