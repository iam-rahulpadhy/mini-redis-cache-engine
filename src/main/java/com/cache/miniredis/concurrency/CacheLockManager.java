package com.cache.miniredis.concurrency;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * CacheLockManager - Centralised read-write lock authority for Mini-Redis.
 *
 * Wraps a single ReentrantReadWriteLock. fair=true prevents write-lock
 * starvation under heavy read load.
 */
public class CacheLockManager {

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);
    private final Lock readLock  = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    public void acquireReadLock()  { readLock.lock();    }
    public void releaseReadLock()  { readLock.unlock();  }
    public void acquireWriteLock() { writeLock.lock();   }
    public void releaseWriteLock() { writeLock.unlock(); }

    public int     getQueueLength() { return rwLock.getQueueLength(); }
    public boolean isWriteLocked()  { return rwLock.isWriteLocked();  }
}
