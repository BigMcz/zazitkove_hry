package cz.mibcon.zazitky.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limit of attempts per key (client IP) — protects access codes and the admin login
 * against guessing. In memory, so it is per application instance; enough for a single instance.
 */
public class RateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int maxAttemptsPerMinute;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public RateLimiter(int maxAttemptsPerMinute, Clock clock) {
        this.maxAttemptsPerMinute = maxAttemptsPerMinute;
        this.clock = clock;
    }

    /** Records an attempt for the key, or throws {@link TooManyRequestsException} when over the limit. */
    public void acquire(String key) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(WINDOW);
        if (attempts.size() > CLEANUP_THRESHOLD) {
            attempts.values().removeIf(times -> {
                synchronized (times) {
                    return times.isEmpty() || !times.peekLast().isAfter(windowStart);
                }
            });
        }

        Deque<Instant> times = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && !times.peekFirst().isAfter(windowStart)) {
                times.pollFirst();
            }
            if (times.size() >= maxAttemptsPerMinute) {
                throw new TooManyRequestsException();
            }
            times.addLast(now);
        }
    }
}
