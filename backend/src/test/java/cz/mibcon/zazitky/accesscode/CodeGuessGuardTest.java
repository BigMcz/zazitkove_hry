package cz.mibcon.zazitky.accesscode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import cz.mibcon.zazitky.common.RateLimiter;
import cz.mibcon.zazitky.common.TooManyRequestsException;
import org.junit.jupiter.api.Test;

class CodeGuessGuardTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC);
    private final CodeGuessGuard guard = new CodeGuessGuard(new RateLimiter(100, clock), new RateLimiter(3, clock));

    @Test
    void unknownCodesFromManyAddressesStopAllAttemptsForAWhile() {
        for (int i = 0; i < 3; i++) {
            String address = "10.0.0." + i;
            assertThatThrownBy(() -> guard.attempt(address, () -> {
                throw new InvalidCodeException();
            })).isInstanceOf(InvalidCodeException.class);
        }

        assertThatThrownBy(() -> guard.attempt("10.0.0.99", () -> "valid"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void validCodesDoNotCountAsFailures() {
        for (int i = 0; i < 10; i++) {
            assertThat(guard.attempt("10.0.1." + i, () -> "valid")).isEqualTo("valid");
        }
    }
}
