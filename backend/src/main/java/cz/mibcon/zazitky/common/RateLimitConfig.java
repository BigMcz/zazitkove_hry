package cz.mibcon.zazitky.common;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Limits per client address use {@code request.getRemoteAddr()} (an IPv6 address by its /64, see
 * {@link RateLimiter#clientKey}). Behind a reverse proxy the proxy must pass the client address and
 * {@code server.forward-headers-strategy} must be set, otherwise all players share one limit.
 */
@Configuration(proxyBeanMethods = false)
public class RateLimitConfig {

    /** Key of the limits counted for the whole application, not per client. */
    public static final String ALL = "all";

    @Bean
    RateLimiter accessCodeRateLimiter(@Value("${app.access-code.attempts-per-minute:10}") int attempts, Clock clock) {
        return new RateLimiter(attempts, clock);
    }

    /** Unknown codes from all clients together: guessing spread over many addresses. */
    @Bean
    RateLimiter accessCodeFailureLimiter(@Value("${app.access-code.failed-attempts-per-minute:300}") int attempts,
            Clock clock) {
        return new RateLimiter(attempts, clock);
    }

    @Bean
    RateLimiter adminLoginRateLimiter(@Value("${app.admin.login-attempts-per-minute:5}") int attempts, Clock clock) {
        return new RateLimiter(attempts, clock);
    }

    /** Wrong passwords per account: guessing one account's password from many addresses. */
    @Bean
    RateLimiter loginFailureLimiter(@Value("${app.admin.login-failures-per-account:10}") int failures, Clock clock) {
        return new RateLimiter(failures, Duration.ofMinutes(15), clock);
    }

    /** Answers, hints and syncs per game: guessing answers online and filling the database. */
    @Bean
    RateLimiter sessionActionRateLimiter(@Value("${app.session.actions-per-minute:30}") int actions, Clock clock) {
        return new RateLimiter(actions, clock);
    }
}
