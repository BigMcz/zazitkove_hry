package cz.mibcon.zazitky.common;

import static org.assertj.core.api.Assertions.assertThat;
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
    void ipv6AddressesOfOneNetworkShareTheLimit() {
        assertThat(RateLimiter.clientKey("2001:db8:1:2:aaaa::1"))
                .isEqualTo(RateLimiter.clientKey("2001:db8:1:2:bbbb:cccc:dddd:eeee"))
                .isEqualTo("20010db800010002::/64");
        assertThat(RateLimiter.clientKey("2001:db8:1:3::1")).isNotEqualTo(RateLimiter.clientKey("2001:db8:1:2::1"));
        assertThat(RateLimiter.clientKey("10.0.0.1")).isEqualTo("10.0.0.1");
    }

    @Test
    void checkDoesNotCountAndRecordCountsEvenOverTheLimit() {
        limiter.check("account");
        limiter.record("account");
        limiter.record("account");
        limiter.check("account");
        limiter.record("account");

        assertThatThrownBy(() -> limiter.check("account")).isInstanceOf(TooManyRequestsException.class);
        limiter.reset("account");
        assertThatCode(() -> limiter.check("account")).doesNotThrowAnyException();
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
