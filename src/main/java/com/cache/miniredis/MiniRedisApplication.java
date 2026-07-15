package com.cache.miniredis;

public class MiniRedisApplication {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Mini-Redis Engine Initialized.");
        System.out.println("Executing TestEngine suite...");
        TestEngine.main(args);
    }
}
