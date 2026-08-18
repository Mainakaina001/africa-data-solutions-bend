package afds.africadatasolution.common.ratelimit;

import java.util.concurrent.atomic.AtomicInteger;

/** Fixed-window request counter — same algorithm express-rate-limit uses by default. */
class WindowCounter {

    private final long windowMs;
    private volatile long windowStart = System.currentTimeMillis();
    private final AtomicInteger count = new AtomicInteger();

    WindowCounter(long windowMs) {
        this.windowMs = windowMs;
    }

    /** Increments and reports whether the NEW count is within the cap. */
    synchronized boolean checkAndIncrement(int max) {
        resetIfExpired();
        return count.incrementAndGet() <= max;
    }

    /** Reports whether the current count already meets/exceeds the cap, without incrementing. */
    synchronized boolean peekExceeds(int max) {
        resetIfExpired();
        return count.get() >= max;
    }

    synchronized void increment() {
        resetIfExpired();
        count.incrementAndGet();
    }

    private void resetIfExpired() {
        long now = System.currentTimeMillis();
        if (now - windowStart >= windowMs) {
            windowStart = now;
            count.set(0);
        }
    }
}
