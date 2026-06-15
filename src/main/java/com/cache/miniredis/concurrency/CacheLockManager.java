package com.cache.miniredis.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * CacheLockManager -- Centralised read-write lock authority for the Mini-Redis engine.
 *
 * Encapsulates a ReentrantReadWriteLock and exposes a clean named API for acquiring
 * and releasing read and write locks. All lock interactions route through this manager.
 *
 * LOCK SEMANTICS:
 *   Read Lock  (shared):    Multiple threads may hold simultaneously when no write lock held.
 *                            Allows concurrent get() calls without serialisation overhead.
 *   Write Lock (exclusive): Only one thread may hold; no read locks may be held simultaneously.
 *                            Ensures put(), remove(), clear(), and eviction are atomic.
 *
 * DEADLOCK PREVENTION RULES:
 *
 *   Rule 1 -- No Read-to-Write Upgrade:
 *     ReentrantReadWriteLock does NOT support direct read -> write upgrade.
 *     Attempting it deadlocks. Always release the read lock FIRST, then acquire the
 *     write lock, then re-validate state (ABA guard).
 *
 *   Rule 2 -- No Lock Inversion:
 *     Never call external code (user callbacks, I/O, blocking operations) while
 *     holding any cache lock. Prevents: Thread A holds write, waits for resource
 *     owned by Thread B which waits for the write lock.
 *
 *   Rule 3 -- No Nested Mixed Acquisition:
 *     Reentrant write-after-write is supported within the same thread.
 *     Read-then-write in the same thread is forbidden.
 *
 *   Rule 4 -- Always try/finally:
 *     Every acquire must have a matching release in a finally block.
 *     Unreleased locks deadlock the entire cache permanently.
 *
 * EVICTION PHASE CHOREOGRAPHY (canonical pattern):
 *   acquireWriteLock();
 *   try {
 *       evictIfAtCapacity();   // calls EvictionPolicy.evictNext() -- under write lock
 *       insertNewNode();       // mutates nodeMap and LRU list -- under write lock
 *   } finally {
 *       releaseWriteLock();
 *   }
 *   Both eviction and insertion are covered by ONE write lock acquisition --
 *   no reader sees a transient over-capacity state.
 *
 * FAIRNESS:
 *   Constructed with fair=true to prevent write-lock starvation under heavy read load.
 *   Trade-off: fair locks have measurable throughput overhead vs. non-fair locks.
 *   If benchmarking reveals a bottleneck, switch to fair=false and implement
 *   application-level write-priority logic.
 *
 * @author  Mini-Redis Engineering Team
 * @version 1.0.0
 * @since   Java 21
 */
@Component
public class CacheLockManager {

    private static final Logger log = LoggerFactory.getLogger(CacheLockManager.class);

    /**
     * The primary read-write lock guarding all cache data structures.
     * fair=true prevents write-lock starvation under sustained read load.
     */
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(/* fair= */ true);

    /** Cached read-lock reference; avoids repeated virtual dispatch on rwLock.readLock(). */
    private final Lock readLock  = rwLock.readLock();

    /** Cached write-lock reference; avoids repeated virtual dispatch on rwLock.writeLock(). */
    private final Lock writeLock = rwLock.writeLock();

    // ── Read lock operations ────────────────────────────────────────

    /**
     * Acquires the shared read lock, blocking if a write lock is currently held.
     *
     * Multiple threads may hold the read lock simultaneously. Blocks only if:
     *   - Another thread holds the write lock, OR
     *   - A write-lock request is queued (fair=true gives writers preference).
     *
     * USAGE (mandatory try-finally):
     *   lockManager.acquireReadLock();
     *   try {
     *       // read-only cache operations
     *   } finally {
     *       lockManager.releaseReadLock();
     *   }
     *
     * DO NOT call while holding the write lock from the same thread.
     */
    public void acquireReadLock() {
        log.trace("Thread [{}] acquiring READ lock", Thread.currentThread().getName());
        readLock.lock();
    }

    /**
     * Releases the shared read lock held by the current thread.
     *
     * PRECONDITION: Calling thread must currently hold the read lock.
     * Releasing without holding throws IllegalMonitorStateException.
     *
     * Must ALWAYS be in a finally block. Failing to release causes permanent
     * lock retention, blocking all subsequent write-lock attempts.
     */
    public void releaseReadLock() {
        readLock.unlock();
        log.trace("Thread [{}] released READ lock", Thread.currentThread().getName());
    }

    // ── Write lock operations ───────────────────────────────────────

    /**
     * Acquires the exclusive write lock, blocking until all current lock holders release.
     *
     * Exclusive: blocks if ANY other thread holds either a read or write lock.
     * Once acquired, all other acquire attempts block until this write lock is released.
     *
     * EVICTION CONTRACT: Must be held for the ENTIRE eviction + insertion sequence.
     * Releasing between eviction and insertion would allow a reader to observe a
     * temporarily over-capacity cache state.
     *
     * USAGE (mandatory try-finally):
     *   lockManager.acquireWriteLock();
     *   try {
     *       // mutate cache data structures
     *   } finally {
     *       lockManager.releaseWriteLock();
     *   }
     *
     * DO NOT call while holding the read lock from the same thread.
     * ReentrantReadWriteLock does NOT support read-to-write upgrade -- deadlock results.
     */
    public void acquireWriteLock() {
        log.debug("Thread [{}] acquiring WRITE lock", Thread.currentThread().getName());
        writeLock.lock();
    }

    /**
     * Releases the exclusive write lock held by the current thread.
     *
     * PRECONDITION: Calling thread must currently hold the write lock.
     * Must ALWAYS be in a finally block. Unreleased write lock deadlocks
     * all cache operations permanently.
     */
    public void releaseWriteLock() {
        writeLock.unlock();
        log.debug("Thread [{}] released WRITE lock", Thread.currentThread().getName());
    }

    // ── Diagnostics ─────────────────────────────────────────────────

    /**
     * Returns the estimated number of threads currently waiting to acquire either lock.
     * Useful for operational monitoring and load-testing diagnostics.
     *
     * Time Complexity: O(1) -- reads from AbstractQueuedSynchronizer queue length.
     *
     * @return estimated number of queued threads waiting for this lock
     */
    public int getQueueLength() {
        return rwLock.getQueueLength();
    }

    /**
     * Returns true if the write lock is currently held by any thread.
     * For diagnostic logging and assertions ONLY -- not for synchronisation decisions.
     *
     * WARNING: Do NOT use as: if (!isWriteLocked()) acquireWriteLock()
     * State may change between the check and the action (TOCTOU race condition).
     *
     * @return true if any thread currently holds the write lock
     */
    public boolean isWriteLocked() {
        return rwLock.isWriteLocked();
    }
}
