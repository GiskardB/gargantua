package ai.gargantua.adapters.ratelimit;

import ai.gargantua.autoconfigure.RequestRateLimiter;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Sliding-window limiter held in this JVM. Exact for a single instance; with several
 * replicas each one enforces the limit on its own share of the traffic, so use the Redis
 * limiter when the limit must hold across the fleet.
 */
public class InMemoryRequestRateLimiter implements RequestRateLimiter {

    /** Above this many tracked keys, an admission also sweeps out idle ones. */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final ConcurrentHashMap<String, ArrayDeque<Long>> windows = new ConcurrentHashMap<>();
    private final LongSupplier clockMillis;

    public InMemoryRequestRateLimiter() {
        this(System::currentTimeMillis);
    }

    /** Test seam: a controllable clock. */
    public InMemoryRequestRateLimiter(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    @Override
    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        long now = clockMillis.getAsLong();
        long cutoff = now - window.toMillis();
        boolean[] admitted = new boolean[1];
        // compute() serialises callers on the same key, so the check and the record are atomic.
        windows.compute(key, (k, timestamps) -> {
            ArrayDeque<Long> deque = timestamps != null ? timestamps : new ArrayDeque<>();
            while (!deque.isEmpty() && deque.peekFirst() <= cutoff) {
                deque.pollFirst();
            }
            if (deque.size() < maxRequests) {
                deque.addLast(now);
                admitted[0] = true;
            }
            return deque;
        });
        if (windows.size() > SWEEP_THRESHOLD) {
            // Keys of callers that went quiet would otherwise stay forever.
            windows.entrySet().removeIf(e -> {
                Long newest = e.getValue().peekLast();
                return newest == null || newest <= cutoff;
            });
        }
        return admitted[0];
    }

    /** Number of keys currently tracked. */
    int trackedKeys() {
        return windows.size();
    }
}
