package com.cache.miniredis;

public class MiniRedisApplication {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Starting Mini-Redis Cache Engine...");
        System.out.println("Executing native TestEngine suite to verify isolation and concurrency...");
        TestEngine.main(args);
    }
}
