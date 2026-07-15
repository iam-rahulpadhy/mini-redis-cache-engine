package com.cache.miniredis;

import com.cache.miniredis.concurrency.CacheLockManager;
import com.cache.miniredis.core.MiniRedisEngine;
import com.cache.miniredis.eviction.LRUEvictionStrategy;
import com.cache.miniredis.core.TenantRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test suite for MiniRedisEngine.
 */
public class TestEngine {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("==================================================");
        System.out.println("       MINI-REDIS ENGINE COMPREHENSIVE TEST       ");
        System.out.println("==================================================\n");

        boolean lruPassed = testLruAccuracy();
        boolean tenantPassed = testTenantIsolation();
        boolean concurrencyPassed = testConcurrencyStress();

        System.out.println("\n==================================================");
        System.out.println("                   TEST SUMMARY                   ");
        System.out.println("==================================================");
        System.out.println("LRU Eviction Accuracy:   " + (lruPassed ? "✅ PASS" : "❌ FAIL"));
        System.out.println("Tenant Isolation Test:   " + (tenantPassed ? "✅ PASS" : "❌ FAIL"));
        System.out.println("Concurrency Stress Test: " + (concurrencyPassed ? "✅ PASS" : "❌ FAIL"));
        System.out.println("==================================================");

        if (!lruPassed || !tenantPassed || !concurrencyPassed) {
            System.exit(1);
        }
    }

    private static boolean testLruAccuracy() {
        System.out.print("[TEST] LRU Eviction Accuracy... ");
        
        CacheLockManager lockManager = new CacheLockManager();
        LRUEvictionStrategy<String, String> lruStrategy = new LRUEvictionStrategy<>();
        MiniRedisEngine<String, String> engine = new MiniRedisEngine<>(5, lruStrategy, lockManager);


        for (int i = 1; i <= 5; i++) {
            engine.put("key" + i, "val" + i, 0);
        }


        engine.get("key1");

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

    private static boolean testTenantIsolation() {
        System.out.print("[TEST] Multi-Tenant Namespace Isolation... ");

        TenantRegistry<String, String> registry = new TenantRegistry<>(10);
        
        MiniRedisEngine<String, String> tenantA = registry.getCache("tenantA");
        MiniRedisEngine<String, String> tenantB = registry.getCache("tenantB");

        tenantA.put("key1", "valueA", 0);
        tenantB.put("key1", "valueB", 0);

        if (!"valueA".equals(tenantA.get("key1")) || !"valueB".equals(tenantB.get("key1"))) {
            System.out.println("FAIL (Namespaces are leaking data)");
            return false;
        }

        registry.dropCache("tenantA");
        if (registry.getNamespaceCount() != 1) {
            System.out.println("FAIL (Namespace was not dropped)");
            return false;
        }

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
                        String key = "key" + rand.nextInt(200);
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

        latch.await();
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
