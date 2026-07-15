package com.cache.miniredis;

import com.cache.miniredis.concurrency.CacheLockManager;
import com.cache.miniredis.core.MiniRedisEngine;
import com.cache.miniredis.eviction.LRUEvictionStrategy;
import com.cache.miniredis.eviction.TtlReaperService;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pure Java Test Harness for Mini-Redis.
 */
public class TestEngine {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("==================================================");
        System.out.println("       MINI-REDIS ENGINE COMPREHENSIVE TEST       ");
        System.out.println("==================================================\n");

        boolean lruPassed = testLruAccuracy();
        boolean ttlPassed = testTtlReaper();
        boolean concurrencyPassed = testConcurrencyStress();

        System.out.println("\n==================================================");
        System.out.println("                   TEST SUMMARY                   ");
        System.out.println("==================================================");
        System.out.println("LRU Eviction Accuracy:   " + (lruPassed ? "✅ PASS" : "❌ FAIL"));
        System.out.println("TTL Background Reaper:   " + (ttlPassed ? "✅ PASS" : "❌ FAIL"));
        System.out.println("Concurrency Stress Test: " + (concurrencyPassed ? "✅ PASS" : "❌ FAIL"));
        System.out.println("==================================================");

        if (!lruPassed || !ttlPassed || !concurrencyPassed) {
            System.exit(1);
        }
    }

    private static boolean testLruAccuracy() {
        System.out.print("[TEST] LRU Eviction Accuracy... ");
        
        CacheLockManager lockManager = new CacheLockManager();
        LRUEvictionStrategy<String, String> lruStrategy = new LRUEvictionStrategy<>();
        MiniRedisEngine<String, String> engine = new MiniRedisEngine<>(5, lruStrategy, lockManager);

        // Insert 5 items (capacity is 5)
        for (int i = 1; i <= 5; i++) {
            engine.put("key" + i, "val" + i, 0);
        }

        // Access key1 to promote it to MRU
        engine.get("key1");

        // Insert 6th item. Because key1 was accessed, key2 is now the LRU.
        engine.put("key6", "val6", 0);

        if (engine.size() != 5) {
            System.out.println("FAIL (Size should be 5, got " + engine.size() + ")");
            return false;
        }

        if (engine.get("key2") != null) {
            System.out.println("FAIL (key2 should have been evicted, but was found)");
            return false;
        }

        if (engine.get("key1") == null) {
            System.out.println("FAIL (key1 should NOT have been evicted)");
            return false;
        }

        System.out.println("✅ PASS");
        return true;
    }

    private static boolean testTtlReaper() throws InterruptedException {
        System.out.print("[TEST] TTL Background Reaper... ");

        CacheLockManager lockManager = new CacheLockManager();
        LRUEvictionStrategy<String, String> lruStrategy = new LRUEvictionStrategy<>();
        MiniRedisEngine<String, String> engine = new MiniRedisEngine<>(10, lruStrategy, lockManager);
        
        TtlReaperService<String, String> reaper = new TtlReaperService<>(engine);
        reaper.start();

        engine.put("tempKey", "tempValue", 300); // 300ms TTL

        if (engine.get("tempKey") == null) {
            System.out.println("FAIL (Key should exist immediately)");
            reaper.shutdown();
            return false;
        }

        // Wait for TTL to expire and reaper to clean it up
        Thread.sleep(500);

        if (engine.size() != 0) {
            System.out.println("FAIL (Reaper did not clear the expired key, size=" + engine.size() + ")");
            reaper.shutdown();
            return false;
        }

        reaper.shutdown();
        System.out.println("✅ PASS");
        return true;
    }

    private static boolean testConcurrencyStress() throws InterruptedException {
        System.out.print("[TEST] Concurrency Stress (50 threads, 10K ops each)... ");

        final int CAPACITY = 100;
        final int THREADS = 50;
        final int OPS_PER_THREAD = 10000;

        CacheLockManager lockManager = new CacheLockManager();
        LRUEvictionStrategy<String, String> lruStrategy = new LRUEvictionStrategy<>();
        MiniRedisEngine<String, String> engine = new MiniRedisEngine<>(CAPACITY, lruStrategy, lockManager);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);
        AtomicInteger exceptions = new AtomicInteger(0);

        for (int i = 0; i < THREADS; i++) {
            executor.submit(() -> {
                try {
                    ThreadLocalRandom rand = ThreadLocalRandom.current();
                    for (int j = 0; j < OPS_PER_THREAD; j++) {
                        String key = "key" + rand.nextInt(200); // 200 possible keys
                        int op = rand.nextInt(3);
                        if (op == 0) {
                            engine.put(key, "value", rand.nextBoolean() ? 50 : 0);
                        } else if (op == 1) {
                            engine.get(key);
                        } else {
                            engine.remove(key);
                        }
                    }
                } catch (Exception e) {
                    exceptions.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // Wait for all threads to finish
        executor.shutdown();

        if (exceptions.get() > 0) {
            System.out.println("FAIL (" + exceptions.get() + " exceptions thrown during stress test)");
            return false;
        }

        if (engine.size() > CAPACITY) {
            System.out.println("FAIL (Capacity invariant violated: size=" + engine.size() + ", max=" + CAPACITY + ")");
            return false;
        }

        System.out.println("✅ PASS (Final Size: " + engine.size() + ")");
        return true;
    }
}
