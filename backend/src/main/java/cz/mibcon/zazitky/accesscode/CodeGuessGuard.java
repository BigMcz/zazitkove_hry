package cz.mibcon.zazitky.accesscode;

import java.util.function.Supplier;

import cz.mibcon.zazitky.common.RateLimitConfig;
import cz.mibcon.zazitky.common.RateLimiter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Protects entering a code against guessing: a limit per client address, and a limit of unknown codes for the
 * whole application, which stops guessing spread over many addresses (players with a valid code then wait too,
 * but only while the attack lasts).
 */
@Component
public class CodeGuessGuard {

    private final RateLimiter perClient;
    private final RateLimiter failures;

    public CodeGuessGuard(@Qualifier("accessCodeRateLimiter") RateLimiter perClient,
            @Qualifier("accessCodeFailureLimiter") RateLimiter failures) {
        this.perClient = perClient;
        this.failures = failures;
    }

    public <T> T attempt(String remoteAddr, Supplier<T> action) {
        failures.check(RateLimitConfig.ALL);
        perClient.acquire(RateLimiter.clientKey(remoteAddr));
        try {
            return action.get();
        } catch (InvalidCodeException e) {
            failures.record(RateLimitConfig.ALL);
            throw e;
        }
    }
}
