package com.cache.miniredis.eviction;

import com.cache.miniredis.core.MiniRedisEngine;

/**
 * TtlReaperService - A background daemon thread that continuously polls the
 * engine's TTL heap for expired entries and reaps them to free memory.
 */
public class TtlReaperService<K, V> implements Runnable {

    private final MiniRedisEngine<K, V> engine;
    private final Thread reaperThread;
    private volatile boolean running;

    public TtlReaperService(MiniRedisEngine<K, V> engine) {
        this.engine = engine;
        this.running = true;
        this.reaperThread = new Thread(this, "ttl-reaper-thread");
        this.reaperThread.setDaemon(true); // Don't block JVM shutdown
    }

    public void start() {
        reaperThread.start();
    }

    public void shutdown() {
        running = false;
        reaperThread.interrupt();
    }

    @Override
    public void run() {
        TtlHeap<K> heap = engine.getTtlHeap();

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                TtlHeap.TtlEntry<K> top = heap.peek();
                
                if (top == null) {
                    Thread.sleep(100);
                    continue;
                }

                if (System.currentTimeMillis() >= top.expiryTime) {
                    TtlHeap.TtlEntry<K> expiredEntry = heap.pop();
                    if (expiredEntry != null) {
                        engine.reapExpired(expiredEntry.key);
                    }
                } else {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("TtlReaperService error: " + e.getMessage());
            }
        }
    }
}
