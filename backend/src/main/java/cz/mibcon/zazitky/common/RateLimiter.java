package cz.mibcon.zazitky.common;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limit of attempts per key (client address, account, session) — protects access codes,
 * answers and the admin login against guessing. In memory, so it is per application instance; enough
 * for a single instance.
 */
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;
    /** More keys at once means an attack from many addresses; new keys are then refused instead of filling the heap. */
    private static final int MAX_KEYS = 100_000;

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public RateLimiter(int maxAttemptsPerMinute, Clock clock) {
        this(maxAttemptsPerMinute, Duration.ofMinutes(1), clock);
    }

    public RateLimiter(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    /**
     * The key of a client address: an IPv4 address as it is, an IPv6 address by its /64 network, because one
     * customer usually gets a whole /64 and could otherwise change the address for every attempt.
     */
    public static String clientKey(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.indexOf(':') < 0) {
            return String.valueOf(remoteAddr);
        }
        try {
            // a literal address, so no DNS lookup
            byte[] address = InetAddress.getByName(remoteAddr).getAddress();
            if (address.length != 16) {
                return remoteAddr;
            }
            return HexFormat.of().formatHex(Arrays.copyOf(address, 8)) + "::/64";
        } catch (UnknownHostException e) {
            return remoteAddr;
        }
    }

    /** Records an attempt for the key, or throws {@link TooManyRequestsException} when over the limit. */
    public void acquire(String key) {
        Deque<Instant> times = timesFor(key);
        Instant now = clock.instant();
        synchronized (times) {
            dropOld(times, now);
            if (times.size() >= maxAttempts) {
                throw new TooManyRequestsException();
            }
            times.addLast(now);
        }
    }

    /** Throws {@link TooManyRequestsException} when the key is over the limit, without recording an attempt. */
    public void check(String key) {
        Deque<Instant> times = attempts.get(key);
        if (times == null) {
            return;
        }
        synchronized (times) {
            dropOld(times, clock.instant());
            if (times.size() >= maxAttempts) {
                throw new TooManyRequestsException();
            }
        }
    }

    /** Records an attempt that already happened (a failed one), even over the limit. */
    public void record(String key) {
        Deque<Instant> times = timesFor(key);
        Instant now = clock.instant();
        synchronized (times) {
            dropOld(times, now);
            times.addLast(now);
        }
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    private Deque<Instant> timesFor(String key) {
        Deque<Instant> times = attempts.get(key);
        if (times != null) {
            return times;
        }
        if (attempts.size() > CLEANUP_THRESHOLD) {
            Instant windowStart = clock.instant().minus(window);
            attempts.values().removeIf(old -> {
                synchronized (old) {
                    return old.isEmpty() || !old.peekLast().isAfter(windowStart);
                }
            });
            if (attempts.size() >= MAX_KEYS) {
                throw new TooManyRequestsException();
            }
        }
        return attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
    }

    private void dropOld(Deque<Instant> times, Instant now) {
        Instant windowStart = now.minus(window);
        while (!times.isEmpty() && !times.peekFirst().isAfter(windowStart)) {
            times.pollFirst();
        }
    }
}
