package cz.mibcon.zazitky.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-28T10:00:00Z"));
    private final RateLimiter limiter = new RateLimiter(3, clock);

    @Test
    void allowsAttemptsUpToTheLimitPerKey() {
        limiter.acquire("1.1.1.1");
        limiter.acquire("1.1.1.1");
        limiter.acquire("1.1.1.1");

        assertThatThrownBy(() -> limiter.acquire("1.1.1.1")).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.acquire("2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void allowsAgainAfterTheWindowPasses() {
        limiter.acquire("1.1.1.1");
        clock.advance(Duration.ofSeconds(30));
        limiter.acquire("1.1.1.1");
        limiter.acquire("1.1.1.1");
        assertThatThrownBy(() -> limiter.acquire("1.1.1.1")).isInstanceOf(TooManyRequestsException.class);

        clock.advance(Duration.ofSeconds(31));

        assertThatCode(() -> limiter.acquire("1.1.1.1")).doesNotThrowAnyException();
    }

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
